package com.ymall.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("t_inventory_txn")
public class InventoryTxn {
    @TableId(type = IdType.INPUT)
    private String txnId;
    private String orderNo;
    private Long skuId;
    private Long warehouseId;
    private Integer deltaAvailable;
    private Integer deltaReserved;
    private String reason;
    private String traceId;
    private Instant createdAt;

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

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

    public Integer getDeltaAvailable() {
        return deltaAvailable;
    }

    public void setDeltaAvailable(Integer deltaAvailable) {
        this.deltaAvailable = deltaAvailable;
    }

    public Integer getDeltaReserved() {
        return deltaReserved;
    }

    public void setDeltaReserved(Integer deltaReserved) {
        this.deltaReserved = deltaReserved;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
