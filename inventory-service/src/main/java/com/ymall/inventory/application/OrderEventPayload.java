package com.ymall.inventory.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderEventPayload {
    private String eventId;
    private String eventType;
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
    private String status;
    private String clientRequestId;
    private Instant occurredAt;
    private String schemaVersion;
    private List<OrderItemPayload> items;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public List<OrderItemPayload> getItems() {
        return items;
    }

    public void setItems(List<OrderItemPayload> items) {
        this.items = items;
    }

    public static class OrderItemPayload {
        private Long skuId;
        private Integer qty;
        private String titleSnapshot;
        private BigDecimal priceSnapshot;
        private String promoSnapshotJson;

        public Long getSkuId() {
            return skuId;
        }

        public void setSkuId(Long skuId) {
            this.skuId = skuId;
        }

        public Integer getQty() {
            return qty;
        }

        public void setQty(Integer qty) {
            this.qty = qty;
        }

        public String getTitleSnapshot() {
            return titleSnapshot;
        }

        public void setTitleSnapshot(String titleSnapshot) {
            this.titleSnapshot = titleSnapshot;
        }

        public BigDecimal getPriceSnapshot() {
            return priceSnapshot;
        }

        public void setPriceSnapshot(BigDecimal priceSnapshot) {
            this.priceSnapshot = priceSnapshot;
        }

        public String getPromoSnapshotJson() {
            return promoSnapshotJson;
        }

        public void setPromoSnapshotJson(String promoSnapshotJson) {
            this.promoSnapshotJson = promoSnapshotJson;
        }
    }
}
