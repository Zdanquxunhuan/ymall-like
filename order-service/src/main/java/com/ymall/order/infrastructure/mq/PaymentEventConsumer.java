package com.ymall.order.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymall.order.application.MqConsumeLogService;
import com.ymall.order.application.OrderPaymentEventService;
import com.ymall.order.application.PaymentEventPayload;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Profile("!test")
@RocketMQMessageListener(topic = "payment-events", consumerGroup = "order-service-payment-group",
        selectorExpression = "PaymentEvent")
public class PaymentEventConsumer implements RocketMQListener<MessageExt> {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private static final String CONSUMER_GROUP = "order-service-payment-group";
    private static final String EVENT_SUCCEEDED = "PaymentSucceeded";

    private final MqConsumeLogService consumeLogService;
    private final OrderPaymentEventService orderPaymentEventService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(MqConsumeLogService consumeLogService,
                                OrderPaymentEventService orderPaymentEventService,
                                ObjectMapper objectMapper) {
        this.consumeLogService = consumeLogService;
        this.orderPaymentEventService = orderPaymentEventService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(MessageExt message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            PaymentEventPayload eventPayload = objectMapper.readValue(payload, PaymentEventPayload.class);
            if (!EVENT_SUCCEEDED.equals(eventPayload.getEventType())) {
                log.info("rocketmq consume ignore eventId={} type={}",
                        eventPayload.getEventId(), eventPayload.getEventType());
                return;
            }
            boolean inserted = consumeLogService.tryInsert(eventPayload.getEventId(), CONSUMER_GROUP);
            if (!inserted) {
                log.info("rocketmq consume skip duplicate eventId={} group={}", eventPayload.getEventId(), CONSUMER_GROUP);
                return;
            }
            OrderPaymentEventService.HandleResult result = orderPaymentEventService.handlePaymentSucceeded(eventPayload);
            if (result == OrderPaymentEventService.HandleResult.IGNORED) {
                consumeLogService.updateStatus(eventPayload.getEventId(), CONSUMER_GROUP, "IGNORED");
            }
            log.info("rocketmq consume payment eventId={} orderNo={} result={} payload={}",
                    eventPayload.getEventId(), eventPayload.getOrderNo(), result, payload);
        } catch (Exception ex) {
            log.error("rocketmq consume parse failed messageId={}", message.getMsgId(), ex);
            throw new RuntimeException(ex);
        }
    }
}
