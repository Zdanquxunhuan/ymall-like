package com.ymall.payment.domain;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("t_pay_callback_log")
public class PayCallbackLog {
    private String payNo;
    private String rawPayload;
    private Integer signatureValid;
    private Instant createdAt;

    public String getPayNo() {
        return payNo;
    }

    public void setPayNo(String payNo) {
        this.payNo = payNo;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public Integer getSignatureValid() {
        return signatureValid;
    }

    public void setSignatureValid(Integer signatureValid) {
        this.signatureValid = signatureValid;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
