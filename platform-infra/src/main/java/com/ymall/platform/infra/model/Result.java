package com.ymall.platform.infra.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ymall.platform.infra.trace.TraceIdUtil;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {
    private final boolean success;
    private final String code;
    private final String message;
    private final String traceId;
    private final T data;

    private Result(boolean success, String code, String message, String traceId, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.traceId = traceId;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(true, "SUCCESS", "OK", TraceIdUtil.currentTraceId(), data);
    }

    public static <T> Result<T> error(String code, String message) {
        return new Result<>(false, code, message, TraceIdUtil.currentTraceId(), null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getTraceId() {
        return traceId;
    }

    public T getData() {
        return data;
    }
}
