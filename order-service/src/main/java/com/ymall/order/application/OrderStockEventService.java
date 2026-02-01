package com.ymall.order.application;

import com.ymall.order.domain.Order;
import com.ymall.order.domain.OrderStateFlow;
import com.ymall.order.domain.OrderStatus;
import com.ymall.order.infrastructure.mapper.OrderMapper;
import com.ymall.order.infrastructure.mapper.OrderStateFlowMapper;
import com.ymall.platform.infra.trace.TraceIdUtil;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

@Service
public class OrderStockEventService {
    public enum HandleResult {
        APPLIED,
        IGNORED
    }

    private final OrderMapper orderMapper;
    private final OrderStateFlowMapper orderStateFlowMapper;

    public OrderStockEventService(OrderMapper orderMapper, OrderStateFlowMapper orderStateFlowMapper) {
        this.orderMapper = orderMapper;
        this.orderStateFlowMapper = orderStateFlowMapper;
    }

    public HandleResult handleStockEvent(InventoryEventPayload payload) {
        String targetStatus = resolveTargetStatus(payload.getEventType());
        if (targetStatus == null) {
            return HandleResult.IGNORED;
        }

        Order order = orderMapper.selectById(payload.getOrderNo());
        if (order == null) {
            insertIgnoredFlow(payload, "ORDER_NOT_FOUND", "NONE");
            return HandleResult.IGNORED;
        }
        if (!OrderStatus.CREATED.name().equalsIgnoreCase(order.getStatus())) {
            insertIgnoredFlow(payload, "STATUS_NOT_CREATED", order.getStatus());
            return HandleResult.IGNORED;
        }

        int updated = orderMapper.updateStatusByStatus(payload.getOrderNo(), OrderStatus.CREATED.name(), targetStatus);
        if (updated == 0) {
            Order latest = orderMapper.selectById(payload.getOrderNo());
            String current = latest == null ? "NONE" : Objects.requireNonNullElse(latest.getStatus(), "NONE");
            insertIgnoredFlow(payload, "CAS_CONFLICT", current);
            return HandleResult.IGNORED;
        }

        insertFlow(payload.getOrderNo(), OrderStatus.CREATED.name(), targetStatus, payload.getEventType(), payload.getEventId(), null);
        return HandleResult.APPLIED;
    }

    private String resolveTargetStatus(String eventType) {
        if ("StockReserved".equals(eventType)) {
            return OrderStatus.STOCK_RESERVED.name();
        }
        if ("StockReserveFailed".equals(eventType)) {
            return OrderStatus.STOCK_FAILED.name();
        }
        return null;
    }

    private void insertIgnoredFlow(InventoryEventPayload payload, String ignoredReason, String currentStatus) {
        insertFlow(payload.getOrderNo(), currentStatus, currentStatus, payload.getEventType(), payload.getEventId(), ignoredReason);
    }

    private void insertFlow(String orderNo, String fromStatus, String toStatus, String event, String eventId,
                            String ignoredReason) {
        OrderStateFlow flow = new OrderStateFlow();
        flow.setOrderNo(orderNo);
        flow.setFromStatus(fromStatus == null ? "NONE" : fromStatus);
        flow.setToStatus(toStatus == null ? "NONE" : toStatus);
        flow.setEvent(event);
        flow.setEventId(eventId);
        flow.setTraceId(TraceIdUtil.currentTraceId());
        flow.setIgnoredReason(ignoredReason);
        flow.setCreatedAt(Instant.now());
        orderStateFlowMapper.insert(flow);
    }
}
