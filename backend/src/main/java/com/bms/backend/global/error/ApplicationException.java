package com.bms.backend.global.error;

public class ApplicationException extends RuntimeException {

    private final ApiErrorCode errorCode;

    public ApplicationException(ApiErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public ApplicationException(ApiErrorCode errorCode, Throwable cause) {
        super(errorCode.name(), cause);
        this.errorCode = errorCode;
    }

    public ApiErrorCode errorCode() {
        return errorCode;
    }
}
