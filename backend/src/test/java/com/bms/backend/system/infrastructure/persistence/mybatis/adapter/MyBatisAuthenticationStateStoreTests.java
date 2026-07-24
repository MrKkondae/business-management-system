package com.bms.backend.system.infrastructure.persistence.mybatis.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bms.backend.system.infrastructure.persistence.mybatis.mapper.AuthenticationStateMapper;
import com.bms.backend.system.infrastructure.persistence.mybatis.model.AuthenticationPersistenceRows.LockedUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisAuthenticationStateStoreTests {

    private static final LocalDateTime PASSWORD_CHANGED_AT =
            LocalDateTime.of(2026, 7, 22, 9, 0);
    private static final LocalDateTime OCCURRED_AT =
            LocalDateTime.of(2026, 7, 23, 9, 0);

    private final AuthenticationStateMapper mapper = mock(AuthenticationStateMapper.class);
    private final MyBatisAuthenticationStateStore store =
            new MyBatisAuthenticationStateStore(mapper);

    @Test
    void deactivatesAndIncrementsTheSecurityVersionOnTheSixthFailure() {
        when(mapper.findUsersForUpdate("USER-01"))
                .thenReturn(List.of(new LockedUser(
                        "USER-01", "ACTIVE", "N", 3, "N", PASSWORD_CHANGED_AT, 5)));
        when(mapper.updateFailedLogin(
                        "USER-01",
                        6,
                        "INACTIVE",
                        "LOGIN_FAILURE",
                        OCCURRED_AT,
                        4,
                        "SYSTEM",
                        OCCURRED_AT))
                .thenReturn(1);

        var update =
                store.recordFailedLogin("USER-01", 3, PASSWORD_CHANGED_AT, OCCURRED_AT)
                        .orElseThrow();

        assertThat(update.loginFailureCount()).isEqualTo(6);
        assertThat(update.accountDeactivated()).isTrue();
        assertThat(update.securityVersion()).isEqualTo(4);
        verify(mapper).findUsersForUpdate("USER-01");
    }

    @Test
    void leavesStateUntouchedWhenTheCredentialBaselineChanged() {
        when(mapper.findUsersForUpdate("USER-01"))
                .thenReturn(List.of(new LockedUser(
                        "USER-01", "ACTIVE", "N", 4, "N", PASSWORD_CHANGED_AT, 2)));

        assertThat(store.recordSuccessfulLogin(
                        "USER-01", 3, PASSWORD_CHANGED_AT, OCCURRED_AT))
                .isEmpty();
    }
}
