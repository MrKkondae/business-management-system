package com.bms.backend.global.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bms.backend.common.application.authentication.LoginSession;
import com.bms.backend.global.error.ApiErrorCode;
import com.bms.backend.global.error.ApplicationException;
import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecentReauthenticationGuardTests {

    @Test
    void allowsBeforeTenMinutesAndRejectsAtTheBoundary() {
        LoginSession session = session(false);

        assertThatCode(() -> guardAt("2026-07-23T09:09:59.999Z").requireRecent(session))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> guardAt("2026-07-23T09:10:00Z").requireRecent(session))
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ApiErrorCode.AUTH_REAUTHENTICATION_REQUIRED));
    }

    @Test
    void rejectsLimitedSessionsBeforeCheckingTheTime() {
        assertThatThrownBy(() ->
                        guardAt("2026-07-23T09:01:00Z").requireRecent(session(true)))
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ApiErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED));
    }

    private RecentReauthenticationGuard guardAt(String instant) {
        return new RecentReauthenticationGuard(
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private LoginSession session(boolean limited) {
        return new LoginSession(
                "USER-01",
                "admin",
                "관리자",
                "ACCESS-01",
                new AuthenticationAuthorizationSnapshot(List.of(), List.of()),
                limited,
                LocalDateTime.of(2026, 7, 22, 9, 0),
                3,
                Instant.parse("2026-07-23T09:00:00Z"),
                Instant.parse("2026-07-23T17:00:00Z"),
                Instant.parse("2026-07-23T09:00:00Z"));
    }
}
