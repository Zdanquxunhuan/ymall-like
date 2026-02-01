package com.ymall.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class InventoryReserveRequest {
    @NotBlank
    private String orderNo;
    @NotNull
    private Long skuId;
    @NotNull
    private Long warehouseId;
    @NotNull
    @Min(1)
    private Integer qty;
    private String clientRequestId;

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

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }
}
