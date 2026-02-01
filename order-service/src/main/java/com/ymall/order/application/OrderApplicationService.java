package com.ymall.order.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymall.order.api.dto.CreateOrderItemRequest;
import com.ymall.order.api.dto.CreateOrderRequest;
import com.ymall.order.api.dto.OrderItemView;
import com.ymall.order.api.dto.OrderResponse;
import com.ymall.order.domain.Order;
import com.ymall.order.domain.OrderItem;
import com.ymall.order.domain.OrderStateFlow;
import com.ymall.order.domain.OrderStatus;
import com.ymall.order.domain.OutboxEvent;
import com.ymall.order.infrastructure.mapper.OrderItemMapper;
import com.ymall.order.infrastructure.mapper.OrderMapper;
import com.ymall.order.infrastructure.mapper.OrderStateFlowMapper;
import com.ymall.order.infrastructure.mapper.OutboxEventMapper;
import com.ymall.platform.infra.exception.BizException;
import com.ymall.platform.infra.exception.ErrorCode;
import com.ymall.platform.infra.trace.TraceIdUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderApplicationService {
    private static final String TOPIC = "order-events";
    private static final String TAG_CREATED = "OrderCreated";
    private static final String TAG_CANCELED = "OrderCanceled";
    private static final String EVENT_SCHEMA_VERSION = "v1";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStateFlowMapper orderStateFlowMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    public OrderApplicationService(OrderMapper orderMapper,
                                   OrderItemMapper orderItemMapper,
                                   OrderStateFlowMapper orderStateFlowMapper,
                                   OutboxEventMapper outboxEventMapper,
                                   ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderStateFlowMapper = orderStateFlowMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        if (request.getClientRequestId() == null || request.getClientRequestId().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "clientRequestId/orderToken 不能为空");
        }
        Order existing = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, request.getUserId())
                .eq(Order::getClientRequestId, request.getClientRequestId()));
        if (existing != null) {
            return buildOrderResponse(existing);
        }
        String orderNo = generateOrderNo();
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(request.getUserId());
        order.setAmount(request.getAmount());
        order.setStatus(OrderStatus.CREATED.name());
        order.setClientRequestId(request.getClientRequestId());
        orderMapper.insert(order);

        List<OrderItem> items = request.getItems().stream()
                .map(item -> toOrderItem(orderNo, item))
                .collect(Collectors.toList());
        if (!items.isEmpty()) {
            orderItemMapper.insertBatch(items);
        }

        String eventId = UUID.randomUUID().toString();
        insertStateFlow(orderNo, null, OrderStatus.CREATED.name(), "CREATE", eventId);
        insertOutboxEvent(eventId, orderNo, TAG_CREATED,
                buildPayload(eventId, TAG_CREATED, order, items));

        return buildOrderResponse(order, items);
    }

    @Transactional
    public OrderResponse cancelOrder(String orderNo) {
        Order order = orderMapper.selectById(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        OrderStatus current = OrderStatus.fromCode(order.getStatus());
        if (current == OrderStatus.CANCELED) {
            return buildOrderResponse(order);
        }
        if (current != OrderStatus.CREATED) {
            throw new BizException(ErrorCode.ORDER_INVALID_STATE, "订单状态不允许取消");
        }
        int updated = orderMapper.updateStatusCas(orderNo, OrderStatus.CREATED.name(), OrderStatus.CANCELED.name(),
                order.getVersion());
        if (updated == 0) {
            throw new BizException(ErrorCode.ORDER_CONCURRENT_MODIFIED, "订单状态更新冲突");
        }
        String eventId = UUID.randomUUID().toString();
        insertStateFlow(orderNo, OrderStatus.CREATED.name(), OrderStatus.CANCELED.name(), "CANCEL", eventId);
        List<OrderItem> items = orderItemMapper.findByOrderNo(orderNo);
        insertOutboxEvent(eventId, orderNo, TAG_CANCELED, buildPayload(eventId, TAG_CANCELED, order, items));
        Order refreshed = orderMapper.selectById(orderNo);
        return buildOrderResponse(Objects.requireNonNullElse(refreshed, order));
    }

    public OrderResponse getOrder(String orderNo) {
        Order order = orderMapper.selectById(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return buildOrderResponse(order);
    }

    private OrderItem toOrderItem(String orderNo, CreateOrderItemRequest item) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderNo(orderNo);
        orderItem.setSkuId(item.getSkuId());
        orderItem.setQty(item.getQty());
        orderItem.setTitleSnapshot(item.getTitleSnapshot());
        orderItem.setPriceSnapshot(item.getPriceSnapshot());
        orderItem.setPromoSnapshotJson(item.getPromoSnapshotJson());
        return orderItem;
    }

    private void insertStateFlow(String orderNo, String fromStatus, String toStatus, String event, String eventId) {
        OrderStateFlow flow = new OrderStateFlow();
        flow.setOrderNo(orderNo);
        flow.setFromStatus(fromStatus == null ? "NONE" : fromStatus);
        flow.setToStatus(toStatus);
        flow.setEvent(event);
        flow.setEventId(eventId);
        flow.setTraceId(TraceIdUtil.currentTraceId());
        flow.setCreatedAt(Instant.now());
        orderStateFlowMapper.insert(flow);
    }

    private void insertOutboxEvent(String eventId, String orderNo, String tag, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setBizKey(orderNo);
        event.setTopic(TOPIC);
        event.setTag(tag);
        event.setPayloadJson(payload);
        event.setStatus("NEW");
        event.setRetryCount(0);
        event.setNextRetryAt(null);
        event.setCreatedAt(Instant.now());
        outboxEventMapper.insert(event);
    }

    private String buildPayload(String eventId, String eventType, Order order, List<OrderItem> items) {
        OrderEventPayload payload = new OrderEventPayload();
        payload.setEventId(eventId);
        payload.setEventType(eventType);
        payload.setOrderNo(order.getOrderNo());
        payload.setUserId(order.getUserId());
        payload.setAmount(order.getAmount());
        payload.setStatus(order.getStatus());
        payload.setClientRequestId(order.getClientRequestId());
        payload.setOccurredAt(Instant.now());
        payload.setSchemaVersion(EVENT_SCHEMA_VERSION);
        List<OrderEventPayload.OrderItemPayload> payloadItems = items.stream()
                .map(item -> {
                    OrderEventPayload.OrderItemPayload itemPayload = new OrderEventPayload.OrderItemPayload();
                    itemPayload.setSkuId(item.getSkuId());
                    itemPayload.setQty(item.getQty());
                    itemPayload.setTitleSnapshot(item.getTitleSnapshot());
                    itemPayload.setPriceSnapshot(item.getPriceSnapshot());
                    itemPayload.setPromoSnapshotJson(item.getPromoSnapshotJson());
                    return itemPayload;
                })
                .collect(Collectors.toList());
        payload.setItems(payloadItems);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "事件序列化失败");
        }
    }

    private OrderResponse buildOrderResponse(Order order) {
        List<OrderItem> items = orderItemMapper.findByOrderNo(order.getOrderNo());
        return buildOrderResponse(order, items);
    }

    private OrderResponse buildOrderResponse(Order order, List<OrderItem> items) {
        OrderResponse response = new OrderResponse();
        response.setOrderNo(order.getOrderNo());
        response.setUserId(order.getUserId());
        response.setAmount(order.getAmount());
        response.setStatus(order.getStatus());
        response.setClientRequestId(order.getClientRequestId());
        response.setCreatedAt(order.getCreatedAt());
        List<OrderItemView> itemViews = items.stream().map(item -> {
            OrderItemView view = new OrderItemView();
            view.setSkuId(item.getSkuId());
            view.setQty(item.getQty());
            view.setTitleSnapshot(item.getTitleSnapshot());
            view.setPriceSnapshot(item.getPriceSnapshot());
            view.setPromoSnapshotJson(item.getPromoSnapshotJson());
            return view;
        }).collect(Collectors.toList());
        response.setItems(itemViews);
        return response;
    }

    private String generateOrderNo() {
        return "O" + UUID.randomUUID().toString().replace("-", "");
    }
}
