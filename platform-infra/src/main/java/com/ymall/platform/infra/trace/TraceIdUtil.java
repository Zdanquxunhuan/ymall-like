package com.ymall.platform.infra.trace;

import org.slf4j.MDC;

import java.util.Optional;

public final class TraceIdUtil {
    public static final String TRACE_ID_KEY = "traceId";

    private TraceIdUtil() {
    }

    public static String currentTraceId() {
        return Optional.ofNullable(MDC.get(TRACE_ID_KEY)).orElse("N/A");
    }
}
