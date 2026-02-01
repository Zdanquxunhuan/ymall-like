package com.ymall.order.domain;

import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

@TableName("t_order_item")
public class OrderItem {
    private String orderNo;
    private Long skuId;
    private Integer qty;
    private String titleSnapshot;
    private BigDecimal priceSnapshot;
    private String promoSnapshotJson;

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
