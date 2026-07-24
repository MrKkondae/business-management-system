package com.bms.backend.system.infrastructure.persistence.mybatis.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bms.backend.common.domain.authentication.NormalizedLoginId;
import com.bms.backend.system.application.authentication.AccountStatus;
import com.bms.backend.system.infrastructure.persistence.mybatis.mapper.AuthenticationUserMapper;
import com.bms.backend.system.infrastructure.persistence.mybatis.model.AuthenticationPersistenceRows.SessionState;
import com.bms.backend.system.infrastructure.persistence.mybatis.model.AuthenticationPersistenceRows.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MyBatisAuthenticationUserQueryTests {

    private static final LocalDateTime PASSWORD_CHANGED_AT =
            LocalDateTime.of(2026, 7, 23, 9, 0);

    private final AuthenticationUserMapper mapper = mock(AuthenticationUserMapper.class);
    private final MyBatisAuthenticationUserQuery query =
            new MyBatisAuthenticationUserQuery(mapper);

    @Test
    void mapsAStoredLoginCandidateWithoutExposingPersistenceRows() {
        when(mapper.findLoginCandidates("admin.user"))
                .thenReturn(List.of(new User(
                        "USER-01",
                        "EMP-01",
                        "admin.user",
                        "관리자",
                        "$argon2id$secret-hash",
                        "ACTIVE",
                        2,
                        PASSWORD_CHANGED_AT,
                        "Y",
                        PASSWORD_CHANGED_AT.plusDays(1),
                        3)));

        var candidate =
                query.findLoginCandidate(NormalizedLoginId.from(" ADMIN.USER ")).orElseThrow();

        assertThat(candidate.userId()).isEqualTo("USER-01");
        assertThat(candidate.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(candidate.passwordChangeRequired()).isTrue();
        assertThat(candidate.securityVersion()).isEqualTo(3);
    }

    @Test
    void rejectsNonCanonicalBooleanFlagsAndDuplicateIdentifiers() {
        when(mapper.findSessionStates("USER-01"))
                .thenReturn(List.of(new SessionState(
                        "USER-01", "ACTIVE", "UNKNOWN", 1, "N", PASSWORD_CHANGED_AT)));
        when(mapper.findSessionStates("DUPLICATE"))
                .thenReturn(List.of(
                        new SessionState(
                                "USER-01", "ACTIVE", "N", 1, "N", PASSWORD_CHANGED_AT),
                        new SessionState(
                                "USER-02", "ACTIVE", "N", 1, "N", PASSWORD_CHANGED_AT)));

        assertThatThrownBy(() -> query.findSessionState("USER-01"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("AUTH_BOOLEAN_FLAG_INVALID");
        assertThatThrownBy(() -> query.findSessionState("DUPLICATE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("AUTH_USER_ID_NOT_UNIQUE");
    }
}
