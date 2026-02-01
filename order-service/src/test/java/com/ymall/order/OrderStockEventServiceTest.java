package com.ymall.order;

import com.ymall.order.api.dto.CreateOrderItemRequest;
import com.ymall.order.api.dto.CreateOrderRequest;
import com.ymall.order.application.InventoryEventPayload;
import com.ymall.order.application.MqConsumeLogService;
import com.ymall.order.application.OrderApplicationService;
import com.ymall.order.application.OrderStockEventService;
import com.ymall.order.domain.Order;
import com.ymall.order.domain.OrderStateFlow;
import com.ymall.order.infrastructure.mapper.OrderMapper;
import com.ymall.order.infrastructure.mapper.OrderStateFlowMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {OrderServiceApplication.class, TestInfraConfig.class})
class OrderStockEventServiceTest {
    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private OrderStockEventService orderStockEventService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderStateFlowMapper orderStateFlowMapper;

    @Autowired
    private MqConsumeLogService consumeLogService;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @Test
    void applyStockReservedUpdatesOrder() {
        String orderNo = createOrder("stock-req-1");
        InventoryEventPayload payload = buildPayload(orderNo, "StockReserved", UUID.randomUUID().toString());

        OrderStockEventService.HandleResult result = orderStockEventService.handleStockEvent(payload);

        Order order = orderMapper.selectById(orderNo);
        List<OrderStateFlow> flows = orderStateFlowMapper.findByOrderNo(orderNo);

        assertThat(result).isEqualTo(OrderStockEventService.HandleResult.APPLIED);
        assertThat(order.getStatus()).isEqualTo("STOCK_RESERVED");
        assertThat(flows).anyMatch(flow -> "STOCK_RESERVED".equals(flow.getToStatus()) && flow.getIgnoredReason() == null);
    }

    @Test
    void outOfOrderEventIsIgnored() {
        String orderNo = createOrder("stock-req-2");
        InventoryEventPayload reserved = buildPayload(orderNo, "StockReserved", UUID.randomUUID().toString());
        InventoryEventPayload failed = buildPayload(orderNo, "StockReserveFailed", UUID.randomUUID().toString());

        orderStockEventService.handleStockEvent(reserved);
        OrderStockEventService.HandleResult result = orderStockEventService.handleStockEvent(failed);

        Order order = orderMapper.selectById(orderNo);
        List<OrderStateFlow> flows = orderStateFlowMapper.findByOrderNo(orderNo);

        assertThat(result).isEqualTo(OrderStockEventService.HandleResult.IGNORED);
        assertThat(order.getStatus()).isEqualTo("STOCK_RESERVED");
        assertThat(flows).anyMatch(flow -> flow.getIgnoredReason() != null);
    }

    @Test
    void consumeLogIsIdempotent() {
        String eventId = "dup-event-1";
        boolean first = consumeLogService.tryInsert(eventId, "order-service-stock-group");
        boolean second = consumeLogService.tryInsert(eventId, "order-service-stock-group");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    private String createOrder(String clientRequestId) {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setSkuId(1001L);
        item.setQty(1);
        item.setTitleSnapshot("测试商品");
        item.setPriceSnapshot(new BigDecimal("88.00"));
        item.setPromoSnapshotJson("{}");

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(1L);
        request.setAmount(new BigDecimal("88.00"));
        request.setClientRequestId(clientRequestId);
        request.setItems(List.of(item));
        return orderApplicationService.createOrder(request).getOrderNo();
    }

    private InventoryEventPayload buildPayload(String orderNo, String eventType, String eventId) {
        InventoryEventPayload payload = new InventoryEventPayload();
        payload.setEventId(eventId);
        payload.setEventType(eventType);
        payload.setOrderNo(orderNo);
        payload.setSkuId(1001L);
        payload.setWarehouseId(1L);
        payload.setQty(1);
        payload.setSchemaVersion("v1");
        payload.setOccurredAt(Instant.now());
        return payload;
    }
}
