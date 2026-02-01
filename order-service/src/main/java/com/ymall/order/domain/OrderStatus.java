package com.ymall.order.domain;

import java.util.Arrays;

public enum OrderStatus {
    CREATED,
    CANCELED,
    UNKNOWN;

    public static OrderStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(code))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
