package com.ymall.inventory.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymall.inventory.application.InventoryApplicationService;
import com.ymall.inventory.application.InventoryReserveCommand;
import com.ymall.inventory.application.InventoryReserveResult;
import com.ymall.inventory.application.MqConsumeLogService;
import com.ymall.inventory.application.OrderEventPayload;
import com.ymall.platform.infra.trace.TraceIdUtil;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Profile("!test")
@RocketMQMessageListener(topic = "order-events", consumerGroup = "inventory-service-group",
        selectorExpression = "OrderEvent")
public class OrderCreatedConsumer implements RocketMQListener<MessageExt> {
    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);
    private static final String CONSUMER_GROUP = "inventory-service-group";
    private static final String EVENT_CREATED = "OrderCreated";

    private final MqConsumeLogService consumeLogService;
    private final InventoryApplicationService inventoryService;
    private final ObjectMapper objectMapper;
    private final Long defaultWarehouseId;

    public OrderCreatedConsumer(MqConsumeLogService consumeLogService,
                                InventoryApplicationService inventoryService,
                                ObjectMapper objectMapper,
                                @Value("${inventory.default-warehouse-id:1}") Long defaultWarehouseId) {
        this.consumeLogService = consumeLogService;
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
        this.defaultWarehouseId = defaultWarehouseId;
    }

    @Override
    public void onMessage(MessageExt message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        String traceId = message.getUserProperty(TraceIdUtil.TRACE_ID_KEY);
        if (traceId != null) {
            MDC.put(TraceIdUtil.TRACE_ID_KEY, traceId);
        }
        try {
            OrderEventPayload eventPayload = objectMapper.readValue(payload, OrderEventPayload.class);
            if (!EVENT_CREATED.equals(eventPayload.getEventType())) {
                log.info("rocketmq consume ignore eventId={} type={}", eventPayload.getEventId(),
                        eventPayload.getEventType());
                return;
            }
            boolean inserted = consumeLogService.tryInsert(eventPayload.getEventId(), CONSUMER_GROUP);
            if (!inserted) {
                log.info("rocketmq consume skip duplicate eventId={} group={}", eventPayload.getEventId(),
                        CONSUMER_GROUP);
                return;
            }
            if (eventPayload.getItems() == null || eventPayload.getItems().isEmpty()) {
                log.warn("rocketmq consume OrderCreated without items eventId={} orderNo={}",
                        eventPayload.getEventId(), eventPayload.getOrderNo());
                return;
            }
            for (OrderEventPayload.OrderItemPayload item : eventPayload.getItems()) {
                InventoryReserveCommand command = new InventoryReserveCommand(
                        eventPayload.getOrderNo(), item.getSkuId(), defaultWarehouseId, item.getQty());
                InventoryReserveResult result = inventoryService.tryReserveAndRecord(command);
                if (result.isSuccess()) {
                    log.info("stock reserved orderNo={} skuId={} qty={} result=success",
                            eventPayload.getOrderNo(), item.getSkuId(), item.getQty());
                } else {
                    log.warn("stock reserve failed orderNo={} skuId={} qty={} code={} reason={}",
                            eventPayload.getOrderNo(), item.getSkuId(), item.getQty(),
                            result.getErrorCode(), result.getErrorReason());
                }
            }
        } catch (Exception ex) {
            log.error("rocketmq consume parse failed messageId={}", message.getMsgId(), ex);
            throw new RuntimeException(ex);
        } finally {
            if (traceId != null) {
                MDC.remove(TraceIdUtil.TRACE_ID_KEY);
            }
        }
    }
}
