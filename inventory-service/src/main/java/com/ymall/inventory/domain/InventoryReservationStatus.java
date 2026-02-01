package com.ymall.inventory.domain;

public enum InventoryReservationStatus {
    RESERVED,
    CONFIRMED,
    RELEASED;

    public static InventoryReservationStatus fromCode(String code) {
        for (InventoryReservationStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + code);
    }

    public boolean canTransitTo(InventoryReservationStatus target) {
        if (this == RESERVED) {
            return target == CONFIRMED || target == RELEASED;
        }
        return false;
    }
}
