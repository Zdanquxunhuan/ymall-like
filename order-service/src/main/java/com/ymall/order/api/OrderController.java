package com.ymall.order.api;

import com.ymall.order.api.dto.CancelOrderRequest;
import com.ymall.order.api.dto.CreateOrderRequest;
import com.ymall.order.api.dto.OrderResponse;
import com.ymall.order.application.OrderApplicationService;
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
@RequestMapping("/orders")
public class OrderController {
    private static final Duration IDEM_TTL = Duration.ofMinutes(10);

    private final OrderApplicationService orderApplicationService;
    private final IdempotencyService idempotencyService;
    private final RateLimiter rateLimiter;

    public OrderController(OrderApplicationService orderApplicationService,
                           IdempotencyService idempotencyService,
                           RateLimiter rateLimiter) {
        this.orderApplicationService = orderApplicationService;
        this.idempotencyService = idempotencyService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public Result<OrderResponse> createOrder(@RequestBody @Valid CreateOrderRequest request,
                                             @RequestHeader(value = "idempotency_key", required = false) String headerKey) {
        String requestKey = firstNonBlank(request.getClientRequestId(), request.getOrderToken());
        String idempotencyKey = resolveIdempotencyKey(headerKey, requestKey);
        if (request.getClientRequestId() == null || request.getClientRequestId().isBlank()) {
            request.setClientRequestId(idempotencyKey);
        }
        String rateKey = "order:create:" + request.getUserId();
        if (!rateLimiter.allow(rateKey, 200, 200)) {
            throw new BizException(ErrorCode.RATE_LIMITED, "订单创建触发限流");
        }
        OrderResponse response = idempotencyService.execute("order:create:" + idempotencyKey, IDEM_TTL,
                OrderResponse.class, () -> orderApplicationService.createOrder(request));
        return Result.ok(response);
    }

    @GetMapping("/{orderNo}")
    public Result<OrderResponse> getOrder(@PathVariable String orderNo) {
        return Result.ok(orderApplicationService.getOrder(orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<OrderResponse> cancelOrder(@PathVariable String orderNo,
                                             @RequestBody(required = false) CancelOrderRequest request,
                                             @RequestHeader(value = "idempotency_key", required = false) String headerKey) {
        String requestKey = request == null ? null : firstNonBlank(request.getClientRequestId(), request.getOrderToken());
        String idempotencyKey = resolveIdempotencyKey(headerKey, requestKey);
        String rateKey = "order:cancel:" + orderNo;
        if (!rateLimiter.allow(rateKey, 200, 200)) {
            throw new BizException(ErrorCode.RATE_LIMITED, "订单取消触发限流");
        }
        OrderResponse response = idempotencyService.execute("order:cancel:" + orderNo + ":" + idempotencyKey,
                IDEM_TTL, OrderResponse.class, () -> orderApplicationService.cancelOrder(orderNo));
        return Result.ok(response);
    }

    private String resolveIdempotencyKey(String headerKey, String requestKey) {
        if (headerKey != null && !headerKey.isBlank() && requestKey != null && !headerKey.equals(requestKey)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "idempotency_key 与 clientRequestId/orderToken 不一致");
        }
        String key = firstNonBlank(headerKey, requestKey);
        if (key == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "缺少幂等键");
        }
        return key;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
