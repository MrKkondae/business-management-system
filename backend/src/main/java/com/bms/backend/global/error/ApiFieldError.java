package com.bms.backend.global.error;

public record ApiFieldError(String field, String reason, String message) {}
