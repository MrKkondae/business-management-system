package com.bms.backend.system.infrastructure.persistence.mybatis.adapter;

import com.bms.backend.common.domain.authentication.NormalizedLoginId;
import com.bms.backend.system.application.authentication.AccountStatus;
import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import com.bms.backend.system.application.authentication.AuthenticationSessionState;
import com.bms.backend.system.application.authentication.AuthenticationUser;
import com.bms.backend.system.application.authentication.AuthenticationUserQuery;
import com.bms.backend.system.application.authentication.ReauthenticationCredential;
import com.bms.backend.system.infrastructure.persistence.mybatis.mapper.AuthenticationUserMapper;
import com.bms.backend.system.infrastructure.persistence.mybatis.model.AuthenticationPersistenceRows;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyBatisAuthenticationUserQuery implements AuthenticationUserQuery {

    private final AuthenticationUserMapper mapper;

    @Override
    public Optional<AuthenticationUser> findLoginCandidate(NormalizedLoginId loginId) {
        return exactlyZeroOrOne(mapper.findLoginCandidates(loginId.value()), "AUTH_LOGIN_ID_NOT_UNIQUE")
                .map(this::toAuthenticationUser);
    }

    @Override
    public AuthenticationAuthorizationSnapshot findAuthorizationSnapshot(String userId) {
        return new AuthenticationAuthorizationSnapshot(
                mapper.findRoles(userId), mapper.findMenus(userId));
    }

    @Override
    public Optional<AuthenticationSessionState> findSessionState(String userId) {
        return exactlyZeroOrOne(mapper.findSessionStates(userId), "AUTH_USER_ID_NOT_UNIQUE")
                .map(this::toSessionState);
    }

    @Override
    public Optional<ReauthenticationCredential> findReauthenticationCredential(String userId) {
        return exactlyZeroOrOne(
                        mapper.findReauthenticationCredentials(userId),
                        "AUTH_USER_ID_NOT_UNIQUE")
                .map(this::toReauthenticationCredential);
    }

    private AuthenticationUser toAuthenticationUser(AuthenticationPersistenceRows.User row) {
        return new AuthenticationUser(
                row.userId(),
                row.employeeId(),
                NormalizedLoginId.from(row.loginId()),
                row.displayName(),
                row.passwordHash(),
                AccountStatus.fromDatabase(row.accountStatusCode()),
                row.loginFailureCount(),
                row.passwordChangedAt(),
                flag(row.passwordChangeRequiredFlag()),
                row.temporaryPasswordExpiresAt(),
                row.securityVersion());
    }

    private AuthenticationSessionState toSessionState(
            AuthenticationPersistenceRows.SessionState row) {
        return new AuthenticationSessionState(
                row.userId(),
                AccountStatus.fromDatabase(row.accountStatusCode()),
                flag(row.deletedFlag()),
                row.securityVersion(),
                flag(row.passwordChangeRequiredFlag()),
                row.passwordChangedAt());
    }

    private ReauthenticationCredential toReauthenticationCredential(
            AuthenticationPersistenceRows.ReauthenticationCredential row) {
        return new ReauthenticationCredential(
                row.userId(),
                row.passwordHash(),
                AccountStatus.fromDatabase(row.accountStatusCode()),
                flag(row.deletedFlag()),
                row.securityVersion(),
                flag(row.passwordChangeRequiredFlag()),
                row.passwordChangedAt());
    }

    private boolean flag(String value) {
        if (value == null) {
            throw new DataIntegrityViolationException("AUTH_BOOLEAN_FLAG_INVALID");
        }
        return switch (value) {
            case "Y" -> true;
            case "N" -> false;
            default -> throw new DataIntegrityViolationException("AUTH_BOOLEAN_FLAG_INVALID");
        };
    }

    private <T> Optional<T> exactlyZeroOrOne(List<T> values, String errorCode) {
        if (values.size() > 1) {
            throw new DataIntegrityViolationException(errorCode);
        }
        return values.stream().findFirst();
    }
}
