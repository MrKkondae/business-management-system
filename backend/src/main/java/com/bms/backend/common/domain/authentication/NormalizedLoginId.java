package com.bms.backend.common.domain.authentication;

import java.util.Locale;

public record NormalizedLoginId(String value) {

    public NormalizedLoginId {
        if (value == null) {
            throw new IllegalArgumentException("LOGIN_ID_REQUIRED");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("LOGIN_ID_REQUIRED");
        }
    }

    public static NormalizedLoginId from(String value) {
        return new NormalizedLoginId(value);
    }

    @Override
    public String toString() {
        return "NormalizedLoginId[redacted]";
    }
}
