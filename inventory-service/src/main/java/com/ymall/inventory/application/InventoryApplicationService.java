package com.ymall.inventory.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymall.inventory.domain.Inventory;
import com.ymall.inventory.domain.InventoryReservation;
import com.ymall.inventory.domain.InventoryReservationStateFlow;
import com.ymall.inventory.domain.InventoryReservationStatus;
import com.ymall.inventory.domain.InventoryTxn;
import com.ymall.inventory.domain.OutboxEvent;
import com.ymall.inventory.infrastructure.mapper.InventoryMapper;
import com.ymall.inventory.infrastructure.mapper.InventoryReservationMapper;
import com.ymall.inventory.infrastructure.mapper.InventoryReservationStateFlowMapper;
import com.ymall.inventory.infrastructure.mapper.InventoryTxnMapper;
import com.ymall.inventory.infrastructure.mapper.OutboxEventMapper;
import com.ymall.inventory.infrastructure.redis.RedisInventoryService;
import com.ymall.platform.infra.exception.ErrorCode;
import com.ymall.platform.infra.trace.TraceIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class InventoryApplicationService {
    private static final Logger log = LoggerFactory.getLogger(InventoryApplicationService.class);
    private static final String STOCK_TOPIC = "inventory-events";
    private static final String STOCK_TAG = "StockEvent";
    private static final String EVENT_STOCK_RESERVED = "StockReserved";
    private static final String EVENT_STOCK_RESERVE_FAILED = "StockReserveFailed";
    private static final String EVENT_SCHEMA_VERSION = "v1";

    private final InventoryMapper inventoryMapper;
    private final InventoryReservationMapper reservationMapper;
    private final InventoryTxnMapper txnMapper;
    private final InventoryReservationStateFlowMapper stateFlowMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final RedisInventoryService redisInventoryService;
    private final ObjectMapper objectMapper;
    private final Duration reservationTtl;

    public InventoryApplicationService(InventoryMapper inventoryMapper,
                                       InventoryReservationMapper reservationMapper,
                                       InventoryTxnMapper txnMapper,
                                       InventoryReservationStateFlowMapper stateFlowMapper,
                                       OutboxEventMapper outboxEventMapper,
                                       RedisInventoryService redisInventoryService,
                                       ObjectMapper objectMapper,
                                       @Value("${inventory.reservation-ttl-seconds:900}") long reservationTtlSeconds) {
        this.inventoryMapper = inventoryMapper;
        this.reservationMapper = reservationMapper;
        this.txnMapper = txnMapper;
        this.stateFlowMapper = stateFlowMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.redisInventoryService = redisInventoryService;
        this.objectMapper = objectMapper;
        this.reservationTtl = Duration.ofSeconds(reservationTtlSeconds);
    }

    public Inventory getInventory(Long skuId, Long warehouseId) {
        Inventory inventory = inventoryMapper.findOne(skuId, warehouseId);
        if (inventory == null) {
            return null;
        }
        return inventory;
    }

    public List<InventoryReservation> getReservationsByOrderNo(String orderNo) {
        return reservationMapper.findByOrderNo(orderNo);
    }

    public InventoryReserveResult tryReserveAndRecord(InventoryReserveCommand command) {
        if (command.getQty() == null || command.getQty() <= 0) {
            return InventoryReserveResult.failure(ErrorCode.VALIDATION_ERROR.getCode(), "预占数量非法");
        }
        String inventoryKey = inventoryKey(command.getWarehouseId(), command.getSkuId());
        String reservationKey = reservationKey(command.getOrderNo(), command.getWarehouseId(), command.getSkuId());
        RedisInventoryService.ReserveStatus reserveStatus = redisInventoryService.tryReserve(
                inventoryKey, reservationKey, command.getQty(), reservationTtl);
        if (reserveStatus == RedisInventoryService.ReserveStatus.DUPLICATE) {
            InventoryReservation reservation = reservationMapper.findOne(
                    command.getOrderNo(), command.getSkuId(), command.getWarehouseId());
            if (reservation != null) {
                return InventoryReserveResult.success(reservation);
            }
            log.warn("redis duplicate but reservation missing orderNo={} skuId={} warehouseId={}",
                    command.getOrderNo(), command.getSkuId(), command.getWarehouseId());
            InventoryReserveResult result = InventoryReserveResult.failure(
                    ErrorCode.INVENTORY_RESERVE_FAILED.getCode(), "幂等检查失败");
            insertOutboxEvent(eventId(), command, result);
            return result;
        }
        if (reserveStatus == RedisInventoryService.ReserveStatus.INSUFFICIENT) {
            InventoryReserveResult result = InventoryReserveResult.failure(
                    ErrorCode.INVENTORY_INSUFFICIENT.getCode(), "库存不足");
            insertOutboxEvent(eventId(), command, result);
            return result;
        }
        if (reserveStatus == RedisInventoryService.ReserveStatus.ERROR) {
            InventoryReserveResult result = InventoryReserveResult.failure(
                    ErrorCode.INVENTORY_RESERVE_FAILED.getCode(), "Redis 预扣失败");
            insertOutboxEvent(eventId(), command, result);
            return result;
        }
        String eventId = eventId();
        try {
            InventoryReservation reservation = doReserveInDatabase(command, eventId);
            return InventoryReserveResult.success(reservation);
        } catch (Exception ex) {
            log.error("reserve db failed orderNo={} skuId={} warehouseId={}",
                    command.getOrderNo(), command.getSkuId(), command.getWarehouseId(), ex);
            redisInventoryService.releaseReservation(inventoryKey, reservationKey);
            InventoryReserveResult result = InventoryReserveResult.failure(
                    ErrorCode.INVENTORY_RESERVE_FAILED.getCode(), "库存预占落库失败");
            insertOutboxEvent(eventId, command, result);
            return result;
        }
    }

    @Transactional
    protected InventoryReservation doReserveInDatabase(InventoryReserveCommand command, String eventId) {
        InventoryReservation existing = reservationMapper.findOne(
                command.getOrderNo(), command.getSkuId(), command.getWarehouseId());
        if (existing != null) {
            return existing;
        }
        Inventory inventory = inventoryMapper.findOne(command.getSkuId(), command.getWarehouseId());
        if (inventory == null) {
            throw new IllegalStateException("inventory not found");
        }
        int updated = inventoryMapper.reserve(command.getSkuId(), command.getWarehouseId(),
                command.getQty(), inventory.getVersion());
        if (updated == 0) {
            throw new IllegalStateException("inventory reserve CAS failed");
        }
        InventoryReservation reservation = new InventoryReservation();
        reservation.setOrderNo(command.getOrderNo());
        reservation.setSkuId(command.getSkuId());
        reservation.setWarehouseId(command.getWarehouseId());
        reservation.setQty(command.getQty());
        reservation.setStatus(InventoryReservationStatus.RESERVED.name());
        reservationMapper.insert(reservation);

        InventoryTxn txn = new InventoryTxn();
        txn.setTxnId(UUID.randomUUID().toString());
        txn.setOrderNo(command.getOrderNo());
        txn.setSkuId(command.getSkuId());
        txn.setWarehouseId(command.getWarehouseId());
        txn.setDeltaAvailable(-command.getQty());
        txn.setDeltaReserved(command.getQty());
        txn.setReason("RESERVE");
        txn.setTraceId(TraceIdUtil.currentTraceId());
        txn.setCreatedAt(Instant.now());
        txnMapper.insert(txn);

        insertStateFlow(command.getOrderNo(), command.getSkuId(), command.getWarehouseId(),
                null, InventoryReservationStatus.RESERVED.name(), "RESERVE", eventId);
        insertOutboxEvent(eventId, command, InventoryReserveResult.success(reservation));
        return reservation;
    }

    @Transactional
    public boolean releaseReservation(InventoryReservation reservation, String reason) {
        InventoryReservationStatus current = InventoryReservationStatus.fromCode(reservation.getStatus());
        if (!current.canTransitTo(InventoryReservationStatus.RELEASED)) {
            return false;
        }
        Inventory inventory = inventoryMapper.findOne(reservation.getSkuId(), reservation.getWarehouseId());
        if (inventory == null) {
            throw new IllegalStateException("inventory not found");
        }
        int updated = reservationMapper.updateStatusCas(reservation.getOrderNo(), reservation.getSkuId(),
                reservation.getWarehouseId(), reservation.getStatus(),
                InventoryReservationStatus.RELEASED.name(), reservation.getVersion());
        if (updated == 0) {
            return false;
        }
        int inventoryUpdated = inventoryMapper.release(reservation.getSkuId(), reservation.getWarehouseId(),
                reservation.getQty(), inventory.getVersion());
        if (inventoryUpdated == 0) {
            throw new IllegalStateException("inventory release CAS failed");
        }
        InventoryTxn txn = new InventoryTxn();
        txn.setTxnId(UUID.randomUUID().toString());
        txn.setOrderNo(reservation.getOrderNo());
        txn.setSkuId(reservation.getSkuId());
        txn.setWarehouseId(reservation.getWarehouseId());
        txn.setDeltaAvailable(reservation.getQty());
        txn.setDeltaReserved(-reservation.getQty());
        txn.setReason(Objects.requireNonNullElse(reason, "RELEASE"));
        txn.setTraceId(TraceIdUtil.currentTraceId());
        txn.setCreatedAt(Instant.now());
        txnMapper.insert(txn);

        insertStateFlow(reservation.getOrderNo(), reservation.getSkuId(), reservation.getWarehouseId(),
                reservation.getStatus(), InventoryReservationStatus.RELEASED.name(), "RELEASE", eventId());
        return true;
    }

    public void syncRedisReservationRelease(InventoryReservation reservation) {
        String inventoryKey = inventoryKey(reservation.getWarehouseId(), reservation.getSkuId());
        String reservationKey = reservationKey(reservation.getOrderNo(), reservation.getWarehouseId(),
                reservation.getSkuId());
        redisInventoryService.releaseReservation(inventoryKey, reservationKey);
    }

    public void insertOutboxEvent(String eventId, String orderNo, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setBizKey(orderNo);
        event.setTopic(STOCK_TOPIC);
        event.setTag(STOCK_TAG);
        event.setPayloadJson(payload);
        event.setStatus("NEW");
        event.setRetryCount(0);
        event.setNextRetryAt(null);
        event.setCreatedAt(Instant.now());
        event.setTraceId(TraceIdUtil.currentTraceId());
        outboxEventMapper.insert(event);
    }

    public InventoryReservation findReservation(String orderNo, Long skuId, Long warehouseId) {
        return reservationMapper.findOne(orderNo, skuId, warehouseId);
    }

    public List<InventoryReservation> findTimeoutReservations(Instant cutoff, int limit) {
        return reservationMapper.findTimeoutReservations(cutoff, limit);
    }

    private void insertStateFlow(String orderNo, Long skuId, Long warehouseId,
                                 String fromStatus, String toStatus, String event, String eventId) {
        InventoryReservationStateFlow flow = new InventoryReservationStateFlow();
        flow.setOrderNo(orderNo);
        flow.setSkuId(skuId);
        flow.setWarehouseId(warehouseId);
        flow.setFromStatus(fromStatus == null ? "NONE" : fromStatus);
        flow.setToStatus(toStatus);
        flow.setEvent(event);
        flow.setEventId(eventId);
        flow.setTraceId(TraceIdUtil.currentTraceId());
        flow.setCreatedAt(Instant.now());
        stateFlowMapper.insert(flow);
    }

    private void insertOutboxEvent(String eventId, InventoryReserveCommand command, InventoryReserveResult result) {
        String payload = result.isSuccess()
                ? buildStockPayload(eventId, EVENT_STOCK_RESERVED, command, result, null, null)
                : buildStockPayload(eventId, EVENT_STOCK_RESERVE_FAILED, command, result,
                result.getErrorCode(), result.getErrorReason());
        insertOutboxEvent(eventId, command.getOrderNo(), payload);
    }

    private String eventId() {
        return UUID.randomUUID().toString();
    }

    private String buildStockPayload(String eventId, String eventType, InventoryReserveCommand command,
                                     InventoryReserveResult result, String errorCode, String errorReason) {
        InventoryEventPayload payload = new InventoryEventPayload();
        payload.setEventId(eventId);
        payload.setEventType(eventType);
        payload.setOrderNo(command.getOrderNo());
        payload.setSkuId(command.getSkuId());
        payload.setWarehouseId(command.getWarehouseId());
        payload.setQty(command.getQty());
        payload.setStatus(result.isSuccess() ? InventoryReservationStatus.RESERVED.name() : "FAILED");
        payload.setErrorCode(errorCode);
        payload.setErrorReason(errorReason);
        payload.setSchemaVersion(EVENT_SCHEMA_VERSION);
        payload.setOccurredAt(Instant.now());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("inventory payload serialize failed");
        }
    }

    private String inventoryKey(Long warehouseId, Long skuId) {
        return "inv:" + warehouseId + ":" + skuId;
    }

    private String reservationKey(String orderNo, Long warehouseId, Long skuId) {
        return "inv:resv:" + orderNo + ":" + warehouseId + ":" + skuId;
    }
}
