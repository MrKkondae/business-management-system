package com.bms.backend.common.application.authentication;

public enum LoginFailureReason {
    USER_NOT_FOUND,
    BAD_CREDENTIALS,
    ACCOUNT_INACTIVE,
    TEMP_PWD_EXPIRED
}
