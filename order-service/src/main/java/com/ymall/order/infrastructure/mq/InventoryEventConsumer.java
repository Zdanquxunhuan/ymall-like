package com.ymall.order.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymall.order.application.InventoryEventPayload;
import com.ymall.order.application.MqConsumeLogService;
import com.ymall.order.application.OrderStockEventService;
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
@RocketMQMessageListener(topic = "inventory-events", consumerGroup = "order-service-stock-group",
        selectorExpression = "StockEvent")
public class InventoryEventConsumer implements RocketMQListener<MessageExt> {
    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);
    private static final String CONSUMER_GROUP = "order-service-stock-group";
    private static final String EVENT_STOCK_RESERVED = "StockReserved";
    private static final String EVENT_STOCK_FAILED = "StockReserveFailed";

    private final MqConsumeLogService consumeLogService;
    private final OrderStockEventService orderStockEventService;
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(MqConsumeLogService consumeLogService,
                                  OrderStockEventService orderStockEventService,
                                  ObjectMapper objectMapper) {
        this.consumeLogService = consumeLogService;
        this.orderStockEventService = orderStockEventService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(MessageExt message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            InventoryEventPayload eventPayload = objectMapper.readValue(payload, InventoryEventPayload.class);
            if (!EVENT_STOCK_RESERVED.equals(eventPayload.getEventType())
                    && !EVENT_STOCK_FAILED.equals(eventPayload.getEventType())) {
                log.info("rocketmq consume ignore eventId={} type={}",
                        eventPayload.getEventId(), eventPayload.getEventType());
                return;
            }
            boolean inserted = consumeLogService.tryInsert(eventPayload.getEventId(), CONSUMER_GROUP);
            if (!inserted) {
                log.info("rocketmq consume skip duplicate eventId={} group={}", eventPayload.getEventId(), CONSUMER_GROUP);
                return;
            }
            OrderStockEventService.HandleResult result = orderStockEventService.handleStockEvent(eventPayload);
            if (result == OrderStockEventService.HandleResult.IGNORED) {
                consumeLogService.updateStatus(eventPayload.getEventId(), CONSUMER_GROUP, "IGNORED");
            }
            log.info("rocketmq consume inventory eventId={} orderNo={} result={} payload={}",
                    eventPayload.getEventId(), eventPayload.getOrderNo(), result, payload);
        } catch (Exception ex) {
            log.error("rocketmq consume parse failed messageId={}", message.getMsgId(), ex);
            throw new RuntimeException(ex);
        }
    }
}
