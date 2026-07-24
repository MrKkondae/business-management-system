package com.bms.backend.system.infrastructure.persistence.mybatis.adapter;

import com.bms.backend.system.application.authentication.AccountStatus;
import com.bms.backend.system.application.authentication.AuthenticationSessionState;
import com.bms.backend.system.application.authentication.AuthenticationStateStore;
import com.bms.backend.system.application.authentication.FailedLoginUpdate;
import com.bms.backend.system.infrastructure.persistence.mybatis.mapper.AuthenticationStateMapper;
import com.bms.backend.system.infrastructure.persistence.mybatis.model.AuthenticationPersistenceRows.LockedUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MyBatisAuthenticationStateStore implements AuthenticationStateStore {

    private static final int DEACTIVATION_FAILURE_COUNT = 6;
    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final AuthenticationStateMapper mapper;

    @Override
    @Transactional
    public Optional<FailedLoginUpdate> recordFailedLogin(
            String userId,
            int expectedSecurityVersion,
            LocalDateTime expectedPasswordChangedAt,
            LocalDateTime occurredAt) {
        Optional<LockedUser> locked =
                lockCurrentUser(userId, expectedSecurityVersion, expectedPasswordChangedAt);
        if (locked.isEmpty()) {
            return Optional.empty();
        }

        LockedUser user = locked.orElseThrow();
        int failureCount = Math.min(user.loginFailureCount() + 1, DEACTIVATION_FAILURE_COUNT);
        boolean deactivated = failureCount >= DEACTIVATION_FAILURE_COUNT;
        int securityVersion = user.securityVersion() + (deactivated ? 1 : 0);
        requireSingleUpdate(mapper.updateFailedLogin(
                userId,
                failureCount,
                deactivated ? AccountStatus.INACTIVE.name() : AccountStatus.ACTIVE.name(),
                deactivated ? "LOGIN_FAILURE" : null,
                deactivated ? occurredAt : null,
                securityVersion,
                SYSTEM_ACTOR,
                occurredAt));
        return Optional.of(new FailedLoginUpdate(failureCount, deactivated, securityVersion));
    }

    @Override
    @Transactional
    public Optional<AuthenticationSessionState> recordSuccessfulLogin(
            String userId,
            int expectedSecurityVersion,
            LocalDateTime expectedPasswordChangedAt,
            LocalDateTime occurredAt) {
        Optional<LockedUser> locked =
                lockCurrentUser(userId, expectedSecurityVersion, expectedPasswordChangedAt);
        if (locked.isEmpty()) {
            return Optional.empty();
        }

        LockedUser user = locked.orElseThrow();
        requireSingleUpdate(
                mapper.updateSuccessfulLogin(userId, occurredAt, SYSTEM_ACTOR, occurredAt));
        return Optional.of(new AuthenticationSessionState(
                user.userId(),
                AccountStatus.ACTIVE,
                false,
                user.securityVersion(),
                flag(user.passwordChangeRequiredFlag()),
                user.passwordChangedAt()));
    }

    private Optional<LockedUser> lockCurrentUser(
            String userId,
            int expectedSecurityVersion,
            LocalDateTime expectedPasswordChangedAt) {
        List<LockedUser> users = mapper.findUsersForUpdate(userId);
        if (users.size() > 1) {
            throw new DataIntegrityViolationException("AUTH_USER_ID_NOT_UNIQUE");
        }
        return users.stream()
                .filter(user -> !flag(user.deletedFlag()))
                .filter(user -> AccountStatus.fromDatabase(user.accountStatusCode())
                        == AccountStatus.ACTIVE)
                .filter(user -> user.securityVersion() == expectedSecurityVersion)
                .filter(user -> user.passwordChangedAt().equals(expectedPasswordChangedAt))
                .findFirst();
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

    private void requireSingleUpdate(int updated) {
        if (updated != 1) {
            throw new DataIntegrityViolationException("AUTH_STATE_UPDATE_COUNT_INVALID");
        }
    }
}
