package com.ymall.order.api.dto;

import java.math.BigDecimal;

public class OrderItemView {
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
