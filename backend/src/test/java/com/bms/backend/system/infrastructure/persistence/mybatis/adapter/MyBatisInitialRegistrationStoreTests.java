package com.bms.backend.system.infrastructure.persistence.mybatis.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bms.backend.system.application.authentication.AccountStatus;
import com.bms.backend.system.infrastructure.persistence.mybatis.mapper.InitialRegistrationMapper;
import com.bms.backend.system.infrastructure.persistence.mybatis.model.AuthenticationPersistenceRows.InitialRegistrationAccount;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MyBatisInitialRegistrationStoreTests {

    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 7, 23, 1, 0);
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 7, 24, 0, 0);

    private final InitialRegistrationMapper mapper = mock(InitialRegistrationMapper.class);
    private final MyBatisInitialRegistrationStore store =
            new MyBatisInitialRegistrationStore(mapper);

    @Test
    void locksAndCompletesAgainstTheSameCredentialBaseline() {
        when(mapper.findAccountsForUpdate("USER-01"))
                .thenReturn(List.of(new InitialRegistrationAccount(
                        "USER-01",
                        "admin",
                        "관리자",
                        "TEMP-HASH",
                        "ACTIVE",
                        "N",
                        "Y",
                        ISSUED_AT,
                        ISSUED_AT.plusHours(24),
                        3)));
        when(mapper.complete(
                        "USER-01",
                        "NEW-HASH",
                        COMPLETED_AT,
                        4,
                        null,
                        "010-1234-5678",
                        3,
                        ISSUED_AT))
                .thenReturn(1);

        var account = store.lockAccount("USER-01").orElseThrow();
        int securityVersion =
                store.complete(account, "NEW-HASH", null, "010-1234-5678", COMPLETED_AT);

        assertThat(account.passwordChangeRequired()).isTrue();
        assertThat(securityVersion).isEqualTo(4);
    }

    @Test
    void rejectsAStaleConditionalUpdate() {
        var account =
                new com.bms.backend.system.application.authentication.InitialRegistrationAccount(
                "USER-01",
                "admin",
                "관리자",
                "TEMP-HASH",
                AccountStatus.ACTIVE,
                false,
                true,
                ISSUED_AT,
                ISSUED_AT.plusHours(24),
                3);

        assertThatThrownBy(() -> store.complete(account, "NEW-HASH", null, null, COMPLETED_AT))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("AUTH_INITIAL_REGISTRATION_UPDATE_INVALID");
    }
}
