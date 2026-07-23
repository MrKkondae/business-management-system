package com.bms.backend.system.application.authentication;

public enum AccountStatus {
    ACTIVE,
    INACTIVE;

    public static AccountStatus fromDatabase(String value) {
        try {
            return AccountStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("AUTH_ACCOUNT_STATUS_INVALID");
        }
    }
}
