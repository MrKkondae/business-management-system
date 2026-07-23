package com.bms.backend.system.application.authentication;

public record FailedLoginUpdate(
        int loginFailureCount, boolean accountDeactivated, int securityVersion) {}
