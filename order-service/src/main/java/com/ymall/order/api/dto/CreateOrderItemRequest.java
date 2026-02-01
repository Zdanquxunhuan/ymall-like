package com.ymall.order.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CreateOrderItemRequest {
    @NotNull
    private Long skuId;
    @NotNull
    @Min(1)
    private Integer qty;
    @NotBlank
    private String titleSnapshot;
    @NotNull
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
