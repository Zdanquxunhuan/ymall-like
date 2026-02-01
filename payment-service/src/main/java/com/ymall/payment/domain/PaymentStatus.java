package com.ymall.payment.domain;

import java.util.EnumSet;
import java.util.Set;

public enum PaymentStatus {
    INIT,
    PAYING,
    SUCCESS,
    FAILED,
    CLOSED;

    public boolean canTransitTo(PaymentStatus target) {
        if (this == target) {
            return true;
        }
        Set<PaymentStatus> allowed = switch (this) {
            case INIT -> EnumSet.of(PAYING, CLOSED);
            case PAYING -> EnumSet.of(SUCCESS, FAILED, CLOSED);
            case SUCCESS -> EnumSet.noneOf(PaymentStatus.class);
            case FAILED -> EnumSet.of(PAYING, CLOSED);
            case CLOSED -> EnumSet.noneOf(PaymentStatus.class);
        };
        return allowed.contains(target);
    }
}
