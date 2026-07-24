package com.bms.backend.common.application.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginSessionTests {

    @Test
    void normalSessionUsesFifteenMinuteIdleAndEightHourAbsoluteExpiry() {
        LoginSession session = LoginSession.from(login(false, null));

        assertThat(session.idleTimeoutSeconds()).isEqualTo(900);
        assertThat(session.absoluteExpiresAt())
                .isEqualTo(Instant.parse("2026-07-23T17:00:00Z"));
        assertThat(session.expirationAt(Instant.parse("2026-07-23T09:14:59Z")))
                .isEqualTo(LoginSession.Expiration.NONE);
        assertThat(session.expirationAt(Instant.parse("2026-07-23T09:15:00Z")))
                .isEqualTo(LoginSession.Expiration.IDLE);
    }

    @Test
    void limitedSessionUsesEarlierTemporaryPasswordExpiry() {
        LoginSession session =
                LoginSession.from(login(true, LocalDateTime.of(2026, 7, 23, 9, 20)));

        assertThat(session.idleTimeoutSeconds()).isEqualTo(600);
        assertThat(session.absoluteExpiresAt())
                .isEqualTo(Instant.parse("2026-07-23T09:20:00Z"));
        LoginSession active = session.withActivityAt(Instant.parse("2026-07-23T09:15:00Z"));
        assertThat(active.expirationAt(Instant.parse("2026-07-23T09:19:59Z")))
                .isEqualTo(LoginSession.Expiration.NONE);
        assertThat(active.expirationAt(Instant.parse("2026-07-23T09:20:00Z")))
                .isEqualTo(LoginSession.Expiration.ABSOLUTE);
    }

    @Test
    void promotesLimitedSessionToANewEightHourNormalSession() {
        LoginSession limited =
                LoginSession.from(login(true, LocalDateTime.of(2026, 7, 23, 9, 20)));
        Instant completedAt = Instant.parse("2026-07-23T09:05:00Z");
        var authorization = new AuthenticationAuthorizationSnapshot(
                List.of(new com.bms.backend.system.application.authentication.AuthenticationRole(
                        "ROLE-01", "시스템관리자")),
                List.of());

        LoginSession promoted = limited.promoted(
                authorization,
                LocalDateTime.of(2026, 7, 23, 9, 5),
                4,
                completedAt);

        assertThat(promoted.passwordChangeRequired()).isFalse();
        assertThat(promoted.securityVersion()).isEqualTo(4);
        assertThat(promoted.authorization()).isEqualTo(authorization);
        assertThat(promoted.lastUserActivityAt()).isEqualTo(completedAt);
        assertThat(promoted.reauthenticatedAt()).isEqualTo(completedAt);
        assertThat(promoted.absoluteExpiresAt()).isEqualTo(completedAt.plusSeconds(28_800));
        assertThat(promoted.idleTimeoutSeconds()).isEqualTo(900);
    }

    private AuthenticatedLogin login(
            boolean passwordChangeRequired, LocalDateTime temporaryPasswordExpiresAt) {
        return new AuthenticatedLogin(
                "USER-01",
                "admin",
                "관리자",
                "ACCESS-01",
                new AuthenticationAuthorizationSnapshot(List.of(), List.of()),
                passwordChangeRequired,
                temporaryPasswordExpiresAt,
                LocalDateTime.of(2026, 7, 22, 9, 0),
                3,
                LocalDateTime.of(2026, 7, 23, 9, 0));
    }
}
