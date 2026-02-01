package com.ymall.order.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymall.order.application.MqConsumeLogService;
import com.ymall.order.application.OrderEventPayload;
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
@RocketMQMessageListener(topic = "order-events", consumerGroup = "order-service-group",
        selectorExpression = "OrderEvent")
public class OrderEventConsumer implements RocketMQListener<MessageExt> {
    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private static final String CONSUMER_GROUP = "order-service-group";
    private static final String EVENT_CREATED = "OrderCreated";

    private final MqConsumeLogService consumeLogService;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(MqConsumeLogService consumeLogService, ObjectMapper objectMapper) {
        this.consumeLogService = consumeLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(MessageExt message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            OrderEventPayload eventPayload = objectMapper.readValue(payload, OrderEventPayload.class);
            if (!EVENT_CREATED.equals(eventPayload.getEventType())) {
                log.info("rocketmq consume ignore eventId={} type={}", eventPayload.getEventId(), eventPayload.getEventType());
                return;
            }
            boolean inserted = consumeLogService.tryInsert(eventPayload.getEventId(), CONSUMER_GROUP);
            if (!inserted) {
                log.info("rocketmq consume skip duplicate eventId={} group={}", eventPayload.getEventId(), CONSUMER_GROUP);
                return;
            }
            log.info("rocketmq consume OrderCreated eventId={} orderNo={} payload={}",
                    eventPayload.getEventId(), eventPayload.getOrderNo(), payload);
        } catch (Exception ex) {
            log.error("rocketmq consume parse failed messageId={}", message.getMsgId(), ex);
            throw new RuntimeException(ex);
        }
    }
}
