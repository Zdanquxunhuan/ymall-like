package com.ymall.inventory.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ymall.platform.infra.mybatis.BaseEntity;

@TableName("t_inventory")
public class Inventory extends BaseEntity {
    private Long skuId;
    private Long warehouseId;
    private Integer availableQty;
    private Integer reservedQty;

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

    public Integer getAvailableQty() {
        return availableQty;
    }

    public void setAvailableQty(Integer availableQty) {
        this.availableQty = availableQty;
    }

    public Integer getReservedQty() {
        return reservedQty;
    }

    public void setReservedQty(Integer reservedQty) {
        this.reservedQty = reservedQty;
    }
}
