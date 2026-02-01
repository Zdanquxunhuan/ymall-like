package com.ymall.payment.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymall.payment.api.dto.CreatePaymentRequest;
import com.ymall.payment.api.dto.PaymentCallbackRequest;
import com.ymall.payment.api.dto.PaymentCallbackResponse;
import com.ymall.payment.api.dto.PaymentResponse;
import com.ymall.payment.domain.OutboxEvent;
import com.ymall.payment.domain.PayCallbackLog;
import com.ymall.payment.domain.PayOrder;
import com.ymall.payment.domain.PayStateFlow;
import com.ymall.payment.domain.PaymentStatus;
import com.ymall.payment.infrastructure.mapper.OutboxEventMapper;
import com.ymall.payment.infrastructure.mapper.PayCallbackLogMapper;
import com.ymall.payment.infrastructure.mapper.PayOrderMapper;
import com.ymall.payment.infrastructure.mapper.PayStateFlowMapper;
import com.ymall.platform.infra.exception.BizException;
import com.ymall.platform.infra.exception.ErrorCode;
import com.ymall.platform.infra.trace.TraceIdUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentApplicationService {
    private static final String TOPIC = "payment-events";
    private static final String TAG = "PaymentEvent";
    private static final String EVENT_SUCCEEDED = "PaymentSucceeded";
    private static final String EVENT_SCHEMA_VERSION = "v1";

    private final PayOrderMapper payOrderMapper;
    private final PayCallbackLogMapper payCallbackLogMapper;
    private final PayStateFlowMapper payStateFlowMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;
    private final Duration reconcileTimeout;

    public PaymentApplicationService(PayOrderMapper payOrderMapper,
                                     PayCallbackLogMapper payCallbackLogMapper,
                                     PayStateFlowMapper payStateFlowMapper,
                                     OutboxEventMapper outboxEventMapper,
                                     ObjectMapper objectMapper,
                                     @Value("${payment.reconcile.timeout-seconds:300}") long timeoutSeconds) {
        this.payOrderMapper = payOrderMapper;
        this.payCallbackLogMapper = payCallbackLogMapper;
        this.payStateFlowMapper = payStateFlowMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.objectMapper = objectMapper;
        this.reconcileTimeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        PayOrder existing = payOrderMapper.findByOrderNo(request.getOrderNo());
        if (existing != null) {
            return toResponse(existing);
        }
        PayOrder payOrder = new PayOrder();
        payOrder.setPayNo(generatePayNo());
        payOrder.setOrderNo(request.getOrderNo());
        payOrder.setAmount(request.getAmount());
        payOrder.setStatus(PaymentStatus.INIT.name());
        payOrder.setChannel(request.getChannel());
        payOrderMapper.insert(payOrder);
        insertStateFlow(payOrder, "NONE", PaymentStatus.INIT.name(), "CREATE", UUID.randomUUID().toString(), null);

        int updated = payOrderMapper.updateStatusByStatus(payOrder.getPayNo(), PaymentStatus.INIT.name(),
                PaymentStatus.PAYING.name());
        if (updated > 0) {
            insertStateFlow(payOrder, PaymentStatus.INIT.name(), PaymentStatus.PAYING.name(),
                    "START_PAY", UUID.randomUUID().toString(), null);
        }
        PayOrder latest = payOrderMapper.selectById(payOrder.getPayNo());
        return toResponse(Objects.requireNonNullElse(latest, payOrder));
    }

    public PaymentResponse getPaymentByPayNo(String payNo) {
        PayOrder payOrder = payOrderMapper.selectById(payNo);
        if (payOrder == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND, "支付单不存在");
        }
        return toResponse(payOrder);
    }

    public PaymentResponse getPaymentByOrderNo(String orderNo) {
        PayOrder payOrder = payOrderMapper.findByOrderNo(orderNo);
        if (payOrder == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND, "支付单不存在");
        }
        return toResponse(payOrder);
    }

    @Transactional
    public PaymentCallbackResponse handleCallback(PaymentCallbackRequest request, String rawPayload) {
        PayOrder payOrder = payOrderMapper.selectById(request.getPayNo());
        if (payOrder == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND, "支付单不存在");
        }
        if (!Objects.equals(payOrder.getOrderNo(), request.getOrderNo())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "订单号不匹配");
        }
        if (!amountEquals(payOrder.getAmount(), request.getAmount())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "金额不匹配");
        }
        insertCallbackLog(request.getPayNo(), rawPayload, true);

        PaymentStatus targetStatus = resolveTargetStatus(request.getStatus());
        if (targetStatus == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "回调状态非法");
        }
        PaymentStatus current = PaymentStatus.valueOf(payOrder.getStatus());
        if (current == targetStatus) {
            insertStateFlow(payOrder, payOrder.getStatus(), payOrder.getStatus(),
                    "CALLBACK_DUP", UUID.randomUUID().toString(), "ALREADY_" + targetStatus.name());
            return buildCallbackResponse(payOrder.getPayNo(), payOrder.getStatus());
        }
        if (!current.canTransitTo(targetStatus)) {
            insertStateFlow(payOrder, payOrder.getStatus(), payOrder.getStatus(),
                    "CALLBACK_IGNORED", UUID.randomUUID().toString(), "INVALID_TRANSITION");
            return buildCallbackResponse(payOrder.getPayNo(), payOrder.getStatus());
        }

        int updated = payOrderMapper.updateStatusCas(payOrder.getPayNo(), payOrder.getStatus(),
                targetStatus.name(), payOrder.getVersion());
        if (updated == 0) {
            PayOrder latest = payOrderMapper.selectById(payOrder.getPayNo());
            String currentStatus = latest == null ? "NONE" : latest.getStatus();
            insertStateFlow(payOrder, payOrder.getStatus(), currentStatus, "CALLBACK_IGNORED",
                    UUID.randomUUID().toString(), "CAS_CONFLICT");
            return buildCallbackResponse(payOrder.getPayNo(), currentStatus);
        }
        insertStateFlow(payOrder, payOrder.getStatus(), targetStatus.name(),
                "CALLBACK", UUID.randomUUID().toString(), null);
        if (targetStatus == PaymentStatus.SUCCESS) {
            insertPaymentSucceededOutbox(payOrder);
        }
        PayOrder refreshed = payOrderMapper.selectById(payOrder.getPayNo());
        String status = refreshed == null ? targetStatus.name() : refreshed.getStatus();
        return buildCallbackResponse(payOrder.getPayNo(), status);
    }

    @Transactional
    public void recordInvalidCallback(String payNo, String rawPayload) {
        insertCallbackLog(payNo, rawPayload, false);
    }

    public List<PayOrder> findTimeoutPayingOrders() {
        Instant deadline = Instant.now().minus(reconcileTimeout);
        return payOrderMapper.findTimeoutPaying(deadline);
    }

    @Transactional
    public void reconcilePayOrder(PayOrder payOrder) {
        PayOrder current = payOrderMapper.selectById(payOrder.getPayNo());
        if (current == null) {
            return;
        }
        PaymentStatus status = PaymentStatus.valueOf(current.getStatus());
        if (status != PaymentStatus.PAYING) {
            return;
        }
        PaymentStatus queryResult = mockQueryStatus(current.getPayNo());
        if (!status.canTransitTo(queryResult)) {
            insertStateFlow(current, current.getStatus(), current.getStatus(),
                    "RECONCILE_IGNORED", UUID.randomUUID().toString(), "INVALID_TRANSITION");
            return;
        }
        int updated = payOrderMapper.updateStatusCas(current.getPayNo(), current.getStatus(),
                queryResult.name(), current.getVersion());
        if (updated == 0) {
            insertStateFlow(current, current.getStatus(), current.getStatus(),
                    "RECONCILE_IGNORED", UUID.randomUUID().toString(), "CAS_CONFLICT");
            return;
        }
        String event = queryResult == PaymentStatus.SUCCESS ? "RECONCILE_SUCCESS" : "RECONCILE_CLOSE";
        insertStateFlow(current, current.getStatus(), queryResult.name(), event, UUID.randomUUID().toString(), null);
        if (queryResult == PaymentStatus.SUCCESS) {
            insertPaymentSucceededOutbox(current);
        }
    }

    public List<PayOrder> listAll() {
        return payOrderMapper.selectList(new LambdaQueryWrapper<>());
    }

    private PaymentStatus resolveTargetStatus(String status) {
        if ("SUCCESS".equalsIgnoreCase(status)) {
            return PaymentStatus.SUCCESS;
        }
        if ("FAILED".equalsIgnoreCase(status)) {
            return PaymentStatus.FAILED;
        }
        if ("CLOSED".equalsIgnoreCase(status)) {
            return PaymentStatus.CLOSED;
        }
        return null;
    }

    private void insertCallbackLog(String payNo, String rawPayload, boolean signatureValid) {
        PayCallbackLog log = new PayCallbackLog();
        log.setPayNo(payNo);
        log.setRawPayload(rawPayload);
        log.setSignatureValid(signatureValid ? 1 : 0);
        log.setCreatedAt(Instant.now());
        payCallbackLogMapper.insert(log);
    }

    private void insertStateFlow(PayOrder payOrder, String fromStatus, String toStatus, String event, String eventId,
                                 String ignoredReason) {
        PayStateFlow flow = new PayStateFlow();
        flow.setPayNo(payOrder.getPayNo());
        flow.setOrderNo(payOrder.getOrderNo());
        flow.setFromStatus(fromStatus == null ? "NONE" : fromStatus);
        flow.setToStatus(toStatus == null ? "NONE" : toStatus);
        flow.setEvent(event);
        flow.setEventId(eventId);
        flow.setTraceId(TraceIdUtil.currentTraceId());
        flow.setIgnoredReason(ignoredReason);
        flow.setCreatedAt(Instant.now());
        payStateFlowMapper.insert(flow);
    }

    private void insertPaymentSucceededOutbox(PayOrder payOrder) {
        String eventId = UUID.randomUUID().toString();
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setBizKey(payOrder.getPayNo());
        event.setTopic(TOPIC);
        event.setTag(TAG);
        event.setPayloadJson(buildPaymentPayload(eventId, payOrder));
        event.setStatus("NEW");
        event.setRetryCount(0);
        event.setNextRetryAt(null);
        event.setCreatedAt(Instant.now());
        event.setTraceId(TraceIdUtil.currentTraceId());
        outboxEventMapper.insert(event);
    }

    private String buildPaymentPayload(String eventId, PayOrder payOrder) {
        PaymentEventPayload payload = new PaymentEventPayload();
        payload.setEventId(eventId);
        payload.setEventType(EVENT_SUCCEEDED);
        payload.setPayNo(payOrder.getPayNo());
        payload.setOrderNo(payOrder.getOrderNo());
        payload.setAmount(payOrder.getAmount());
        payload.setTraceId(TraceIdUtil.currentTraceId());
        payload.setSchemaVersion(EVENT_SCHEMA_VERSION);
        payload.setOccurredAt(Instant.now());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "支付事件序列化失败");
        }
    }

    private PaymentStatus mockQueryStatus(String payNo) {
        return Math.abs(payNo.hashCode()) % 2 == 0 ? PaymentStatus.SUCCESS : PaymentStatus.CLOSED;
    }

    private PaymentResponse toResponse(PayOrder order) {
        PaymentResponse response = new PaymentResponse();
        response.setPayNo(order.getPayNo());
        response.setOrderNo(order.getOrderNo());
        response.setAmount(order.getAmount());
        response.setStatus(order.getStatus());
        response.setChannel(order.getChannel());
        response.setCreatedAt(order.getCreatedAt());
        return response;
    }

    private String generatePayNo() {
        return "P" + UUID.randomUUID().toString().replace("-", "");
    }

    private PaymentCallbackResponse buildCallbackResponse(String payNo, String status) {
        PaymentCallbackResponse response = new PaymentCallbackResponse();
        response.setPayNo(payNo);
        response.setStatus(status);
        return response;
    }

    private boolean amountEquals(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }
}
