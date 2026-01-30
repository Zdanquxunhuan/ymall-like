package com.ymall.platform.infra.exception;

public enum ErrorCode {
    SYSTEM_ERROR("SYS-500", "系统异常"),
    VALIDATION_ERROR("REQ-400", "参数校验失败"),
    IDEMPOTENT_CONFLICT("IDEMP-409", "幂等冲突"),
    RATE_LIMITED("RATE-429", "请求被限流"),
    MQ_SEND_FAILED("MQ-500", "消息发送失败");

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
