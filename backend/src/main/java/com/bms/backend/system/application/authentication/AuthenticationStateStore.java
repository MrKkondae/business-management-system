package com.bms.backend.system.application.authentication;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthenticationStateStore {

    Optional<FailedLoginUpdate> recordFailedLogin(
            String userId,
            int expectedSecurityVersion,
            LocalDateTime expectedPasswordChangedAt,
            LocalDateTime occurredAt);

    Optional<AuthenticationSessionState> recordSuccessfulLogin(
            String userId,
            int expectedSecurityVersion,
            LocalDateTime expectedPasswordChangedAt,
            LocalDateTime occurredAt);
}
