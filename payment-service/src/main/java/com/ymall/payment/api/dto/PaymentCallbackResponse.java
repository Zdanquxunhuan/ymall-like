package com.ymall.payment.api.dto;

public class PaymentCallbackResponse {
    private String payNo;
    private String status;

    public String getPayNo() {
        return payNo;
    }

    public void setPayNo(String payNo) {
        this.payNo = payNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
