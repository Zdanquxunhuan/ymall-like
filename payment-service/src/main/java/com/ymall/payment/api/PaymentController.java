package com.ymall.payment.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymall.payment.api.dto.CreatePaymentRequest;
import com.ymall.payment.api.dto.PaymentCallbackRequest;
import com.ymall.payment.api.dto.PaymentCallbackResponse;
import com.ymall.payment.api.dto.PaymentResponse;
import com.ymall.payment.application.PaymentApplicationService;
import com.ymall.payment.application.PaymentSignatureService;
import com.ymall.platform.infra.exception.BizException;
import com.ymall.platform.infra.exception.ErrorCode;
import com.ymall.platform.infra.idempotency.IdempotencyService;
import com.ymall.platform.infra.model.Result;
import com.ymall.platform.infra.ratelimit.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/payments")
public class PaymentController {
    private static final Duration IDEM_TTL = Duration.ofMinutes(10);

    private final PaymentApplicationService paymentApplicationService;
    private final PaymentSignatureService paymentSignatureService;
    private final IdempotencyService idempotencyService;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentApplicationService paymentApplicationService,
                             PaymentSignatureService paymentSignatureService,
                             IdempotencyService idempotencyService,
                             RateLimiter rateLimiter,
                             ObjectMapper objectMapper) {
        this.paymentApplicationService = paymentApplicationService;
        this.paymentSignatureService = paymentSignatureService;
        this.idempotencyService = idempotencyService;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public Result<PaymentResponse> createPayment(@RequestBody @Valid CreatePaymentRequest request,
                                                 @RequestHeader(value = "idempotency_key", required = false) String headerKey) {
        String idempotencyKey = resolveIdempotencyKey(headerKey, request.getClientRequestId(), request.getOrderNo());
        String rateKey = "payment:create:" + request.getOrderNo();
        if (!rateLimiter.allow(rateKey, 200, 200)) {
            throw new BizException(ErrorCode.RATE_LIMITED, "支付创建触发限流");
        }
        PaymentResponse response = idempotencyService.execute("payment:create:" + idempotencyKey, IDEM_TTL,
                PaymentResponse.class, () -> paymentApplicationService.createPayment(request));
        return Result.ok(response);
    }

    @PostMapping("/mock-callback")
    public Result<PaymentCallbackResponse> mockCallback(@RequestBody @Valid PaymentCallbackRequest request,
                                                        @RequestHeader(value = "idempotency_key", required = false) String headerKey,
                                                        @RequestHeader(value = "X-Signature", required = false) String signature) {
        String rawPayload = serializePayload(request);
        String signPayload = buildSignaturePayload(request);
        boolean signatureValid = paymentSignatureService.verify(signPayload, signature);
        if (!signatureValid) {
            paymentApplicationService.recordInvalidCallback(request.getPayNo(), rawPayload);
            throw new BizException(ErrorCode.PAYMENT_SIGNATURE_INVALID, "签名校验失败");
        }
        String idempotencyKey = resolveIdempotencyKey(headerKey, request.getClientRequestId(), request.getPayNo());
        String rateKey = "payment:callback:" + request.getPayNo();
        if (!rateLimiter.allow(rateKey, 500, 500)) {
            throw new BizException(ErrorCode.RATE_LIMITED, "支付回调触发限流");
        }
        PaymentCallbackResponse response = idempotencyService.execute("payment:callback:" + idempotencyKey, IDEM_TTL,
                PaymentCallbackResponse.class, () -> paymentApplicationService.handleCallback(request, rawPayload));
        return Result.ok(response);
    }

    @GetMapping("/{payNo}")
    public Result<PaymentResponse> getPayment(@PathVariable String payNo) {
        return Result.ok(paymentApplicationService.getPaymentByPayNo(payNo));
    }

    @GetMapping("/by-order/{orderNo}")
    public Result<PaymentResponse> getPaymentByOrder(@PathVariable String orderNo) {
        return Result.ok(paymentApplicationService.getPaymentByOrderNo(orderNo));
    }

    private String resolveIdempotencyKey(String headerKey, String requestKey, String fallbackKey) {
        if (headerKey != null && !headerKey.isBlank() && requestKey != null && !headerKey.equals(requestKey)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "idempotency_key 与 clientRequestId 不一致");
        }
        String key = firstNonBlank(headerKey, requestKey, fallbackKey);
        if (key == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "缺少幂等键");
        }
        return key;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String buildSignaturePayload(PaymentCallbackRequest request) {
        return request.getPayNo() + "|" + request.getOrderNo() + "|" + request.getAmount().toPlainString() + "|" + request.getStatus();
    }

    private String serializePayload(PaymentCallbackRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "回调序列化失败");
        }
    }
}
