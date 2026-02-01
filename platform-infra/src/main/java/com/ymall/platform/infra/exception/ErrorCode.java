package com.ymall.platform.infra.exception;

public enum ErrorCode {
    SYSTEM_ERROR("SYS-500", "系统异常"),
    VALIDATION_ERROR("REQ-400", "参数校验失败"),
    IDEMPOTENT_CONFLICT("IDEMP-409", "幂等冲突"),
    RATE_LIMITED("RATE-429", "请求被限流"),
    MQ_SEND_FAILED("MQ-500", "消息发送失败"),
    NOT_FOUND("RES-404", "资源不存在"),
    ORDER_INVALID_STATE("ORDER-409", "订单状态非法"),
    ORDER_CONCURRENT_MODIFIED("ORDER-412", "订单并发冲突"),
    INVENTORY_INSUFFICIENT("INV-409", "库存不足"),
    INVENTORY_RESERVE_FAILED("INV-500", "库存预占失败"),
    INVENTORY_NOT_FOUND("INV-404", "库存不存在"),
    INVENTORY_CONCURRENT_MODIFIED("INV-412", "库存并发冲突");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
