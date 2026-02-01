package com.ymall.order.api.dto;

import java.time.Instant;
import java.util.List;

public class StockEventDemoRequest {
    private String orderNo;
    private Long skuId;
    private Long warehouseId;
    private Integer qty;
    private List<StockEventItem> events;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public List<StockEventItem> getEvents() {
        return events;
    }

    public void setEvents(List<StockEventItem> events) {
        this.events = events;
    }

    public static class StockEventItem {
        private String eventType;
        private String eventId;
        private Instant eventTime;

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getEventId() {
            return eventId;
        }

        public void setEventId(String eventId) {
            this.eventId = eventId;
        }

        public Instant getEventTime() {
            return eventTime;
        }

        public void setEventTime(Instant eventTime) {
            this.eventTime = eventTime;
        }
    }
}
