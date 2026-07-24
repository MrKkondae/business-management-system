package com.bms.backend.system.infrastructure.persistence.mybatis.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bms.backend.common.application.authentication.LoginAttemptContext;
import com.bms.backend.common.application.authentication.LoginFailureReason;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import com.bms.backend.system.infrastructure.persistence.mybatis.mapper.AuthenticationAuditMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MyBatisAuthenticationAuditStoreTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 9, 0);
    private static final LoginAttemptContext CONTEXT =
            new LoginAttemptContext("TRACE-01", "203.0.113.10", "JUnit/1.0");

    private final AuthenticationAuditMapper mapper = mock(AuthenticationAuditMapper.class);
    private final MonotonicUlidGenerator ulidGenerator = mock(MonotonicUlidGenerator.class);
    private final MyBatisAuthenticationAuditStore store =
            new MyBatisAuthenticationAuditStore(mapper, ulidGenerator);

    @Test
    void recordsUnknownUsersUsingOnlyTheProtectedLoginIdentifier() {
        when(ulidGenerator.next()).thenReturn("01KZ0000000000000000000001");
        when(mapper.insertAccessLog(
                        "01KZ0000000000000000000001",
                        null,
                        NOW,
                        "203.0.113.10",
                        "N",
                        "SYSTEM",
                        "USER_NOT_FOUND",
                        "TRACE-01",
                        "hmac-sha256:protected",
                        "JUnit/1.0"))
                .thenReturn(1);

        store.recordFailedLogin(
                null,
                "hmac-sha256:protected",
                LoginFailureReason.USER_NOT_FOUND,
                CONTEXT,
                NOW);

        verify(mapper).insertAccessLog(
                "01KZ0000000000000000000001",
                null,
                NOW,
                "203.0.113.10",
                "N",
                "SYSTEM",
                "USER_NOT_FOUND",
                "TRACE-01",
                "hmac-sha256:protected",
                "JUnit/1.0");
        assertThat(CONTEXT.requestTraceId()).isEqualTo("TRACE-01");
    }
}
