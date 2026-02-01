package com.ymall.inventory.application;

public class InventoryReserveCommand {
    private final String orderNo;
    private final Long skuId;
    private final Long warehouseId;
    private final Integer qty;

    public InventoryReserveCommand(String orderNo, Long skuId, Long warehouseId, Integer qty) {
        this.orderNo = orderNo;
        this.skuId = skuId;
        this.warehouseId = warehouseId;
        this.qty = qty;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getSkuId() {
        return skuId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Integer getQty() {
        return qty;
    }
}
