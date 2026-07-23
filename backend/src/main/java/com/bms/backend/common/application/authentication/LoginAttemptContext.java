package com.bms.backend.common.application.authentication;

import java.util.Objects;

public record LoginAttemptContext(
        String requestTraceId, String clientIpAddress, String userAgent) {

    private static final int MAX_USER_AGENT_LENGTH = 512;

    public LoginAttemptContext {
        requestTraceId = required(requestTraceId, "LOGIN_TRACE_ID_REQUIRED", 64);
        clientIpAddress = required(clientIpAddress, "LOGIN_CLIENT_IP_REQUIRED", 45);
        userAgent = sanitizeUserAgent(userAgent);
    }

    private static String required(String value, String errorCode, int maxLength) {
        Objects.requireNonNull(value, errorCode);
        if (value.isBlank() || value.length() > maxLength || containsControl(value)) {
            throw new IllegalArgumentException(errorCode);
        }
        return value;
    }

    private static String sanitizeUserAgent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        StringBuilder sanitized =
                new StringBuilder(Math.min(value.length(), MAX_USER_AGENT_LENGTH));
        for (int index = 0;
                index < value.length() && sanitized.length() < MAX_USER_AGENT_LENGTH;
                index++) {
            char character = value.charAt(index);
            sanitized.append(Character.isISOControl(character) ? ' ' : character);
        }
        return sanitized.toString().trim();
    }

    private static boolean containsControl(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
