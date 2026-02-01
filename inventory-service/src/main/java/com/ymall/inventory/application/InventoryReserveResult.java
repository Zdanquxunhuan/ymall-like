package com.ymall.inventory.application;

import com.ymall.inventory.domain.InventoryReservation;

public class InventoryReserveResult {
    private boolean success;
    private String errorCode;
    private String errorReason;
    private InventoryReservation reservation;

    public InventoryReserveResult() {
    }

    private InventoryReserveResult(boolean success, String errorCode, String errorReason, InventoryReservation reservation) {
        this.success = success;
        this.errorCode = errorCode;
        this.errorReason = errorReason;
        this.reservation = reservation;
    }

    public static InventoryReserveResult success(InventoryReservation reservation) {
        return new InventoryReserveResult(true, null, null, reservation);
    }

    public static InventoryReserveResult failure(String errorCode, String errorReason) {
        return new InventoryReserveResult(false, errorCode, errorReason, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
    }

    public InventoryReservation getReservation() {
        return reservation;
    }

    public void setReservation(InventoryReservation reservation) {
        this.reservation = reservation;
    }
}
