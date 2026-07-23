package com.bms.backend.global.error;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    COMMON_VALIDATION_FAILED(
            HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요.", DiagnosticLevel.INFO),
    AUTH_AUTHENTICATION_REQUIRED(
            HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.", DiagnosticLevel.INFO),
    AUTH_LOGIN_FAILED(
            HttpStatus.UNAUTHORIZED, "로그인 정보를 확인해 주세요.", DiagnosticLevel.WARN),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.", DiagnosticLevel.WARN),
    AUTH_CSRF_INVALID(
            HttpStatus.FORBIDDEN, "요청을 다시 시도해 주세요.", DiagnosticLevel.WARN),
    AUTH_REAUTHENTICATION_REQUIRED(
            HttpStatus.FORBIDDEN,
            "보안을 위해 비밀번호를 다시 확인해 주세요.",
            DiagnosticLevel.INFO),
    AUTH_REAUTHENTICATION_FAILED(
            HttpStatus.FORBIDDEN, "비밀번호를 확인해 주세요.", DiagnosticLevel.WARN),
    AUTH_PASSWORD_CHANGE_REQUIRED(
            HttpStatus.FORBIDDEN, "초기 비밀번호를 변경해 주세요.", DiagnosticLevel.INFO),
    AUTH_TOO_MANY_ATTEMPTS(
            HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요.", DiagnosticLevel.WARN),
    COMMON_RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없습니다.", DiagnosticLevel.INFO),
    COMMON_DUPLICATE_RESOURCE(
            HttpStatus.CONFLICT, "이미 등록된 정보입니다.", DiagnosticLevel.INFO),
    COMMON_INVALID_STATE(
            HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없습니다.", DiagnosticLevel.INFO),
    COMMON_CONCURRENT_MODIFICATION(
            HttpStatus.CONFLICT,
            "다른 사용자가 먼저 변경했습니다. 다시 조회해 주세요.",
            DiagnosticLevel.WARN),
    COMMON_INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "일시적인 오류가 발생했습니다.",
            DiagnosticLevel.ERROR),
    SYS_LOGIN_ID_DUPLICATE(
            HttpStatus.CONFLICT, "이미 사용 중인 로그인ID입니다.", DiagnosticLevel.INFO),
    RES_EMPLOYEE_NUMBER_DUPLICATE(
            HttpStatus.CONFLICT, "이미 사용 중인 사원번호입니다.", DiagnosticLevel.INFO);

    private final HttpStatus status;
    private final String userMessage;
    private final DiagnosticLevel diagnosticLevel;

    ApiErrorCode(HttpStatus status, String userMessage, DiagnosticLevel diagnosticLevel) {
        this.status = status;
        this.userMessage = userMessage;
        this.diagnosticLevel = diagnosticLevel;
    }

    public HttpStatus status() {
        return status;
    }

    public String userMessage() {
        return userMessage;
    }

    public DiagnosticLevel diagnosticLevel() {
        return diagnosticLevel;
    }

    public enum DiagnosticLevel {
        INFO,
        WARN,
        ERROR
    }
}
