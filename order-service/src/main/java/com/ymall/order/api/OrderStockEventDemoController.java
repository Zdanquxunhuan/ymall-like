package com.ymall.order.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymall.order.api.dto.StockEventDemoRequest;
import com.ymall.order.application.InventoryEventPayload;
import com.ymall.platform.infra.exception.BizException;
import com.ymall.platform.infra.exception.ErrorCode;
import com.ymall.platform.infra.mq.ProducerTemplate;
import com.ymall.platform.infra.model.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders/stock-events")
public class OrderStockEventDemoController {
    private static final String STOCK_TOPIC = "inventory-events";
    private static final String STOCK_TAG = "StockEvent";
    private static final String SCHEMA_VERSION = "v1";

    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;

    public OrderStockEventDemoController(ProducerTemplate producerTemplate, ObjectMapper objectMapper) {
        this.producerTemplate = producerTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/demo")
    public Result<List<String>> sendDemoEvents(@RequestBody StockEventDemoRequest request) {
        if (request.getOrderNo() == null || request.getOrderNo().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "orderNo 不能为空");
        }
        if (request.getEvents() == null || request.getEvents().isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "events 不能为空");
        }
        List<String> eventIds = new ArrayList<>();
        for (StockEventDemoRequest.StockEventItem item : request.getEvents()) {
            String eventId = item.getEventId() == null || item.getEventId().isBlank()
                    ? UUID.randomUUID().toString()
                    : item.getEventId();
            InventoryEventPayload payload = new InventoryEventPayload();
            payload.setEventId(eventId);
            payload.setEventType(item.getEventType());
            payload.setOrderNo(request.getOrderNo());
            payload.setSkuId(request.getSkuId());
            payload.setWarehouseId(request.getWarehouseId());
            payload.setQty(request.getQty());
            payload.setSchemaVersion(SCHEMA_VERSION);
            payload.setOccurredAt(item.getEventTime() == null ? Instant.now() : item.getEventTime());
            try {
                String json = objectMapper.writeValueAsString(payload);
                producerTemplate.send(STOCK_TOPIC + ":" + STOCK_TAG, eventId, json);
                eventIds.add(eventId);
            } catch (Exception ex) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "库存事件注入失败");
            }
        }
        return Result.ok(eventIds);
    }
}
