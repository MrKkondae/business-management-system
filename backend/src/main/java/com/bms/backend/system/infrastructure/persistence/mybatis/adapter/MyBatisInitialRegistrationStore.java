package com.bms.backend.system.infrastructure.persistence.mybatis.adapter;

import com.bms.backend.system.application.authentication.AccountStatus;
import com.bms.backend.system.application.authentication.InitialRegistrationAccount;
import com.bms.backend.system.application.authentication.InitialRegistrationStore;
import com.bms.backend.system.infrastructure.persistence.mybatis.mapper.InitialRegistrationMapper;
import com.bms.backend.system.infrastructure.persistence.mybatis.model.AuthenticationPersistenceRows;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisInitialRegistrationStore implements InitialRegistrationStore {

    private final InitialRegistrationMapper mapper;

    @Override
    public Optional<InitialRegistrationAccount> lockAccount(String userId) {
        List<AuthenticationPersistenceRows.InitialRegistrationAccount> accounts =
                mapper.findAccountsForUpdate(userId);
        if (accounts.size() > 1) {
            throw new DataIntegrityViolationException("AUTH_USER_ID_NOT_UNIQUE");
        }
        return accounts.stream().findFirst().map(this::toAccount);
    }

    @Override
    public int complete(
            InitialRegistrationAccount account,
            String newPasswordHash,
            String emailAddress,
            String mobileNumber,
            LocalDateTime completedAt) {
        int newSecurityVersion = account.securityVersion() + 1;
        int updated = mapper.complete(
                account.userId(),
                newPasswordHash,
                completedAt,
                newSecurityVersion,
                emailAddress,
                mobileNumber,
                account.securityVersion(),
                account.passwordChangedAt());
        if (updated != 1) {
            throw new DataIntegrityViolationException("AUTH_INITIAL_REGISTRATION_UPDATE_INVALID");
        }
        return newSecurityVersion;
    }

    private InitialRegistrationAccount toAccount(
            AuthenticationPersistenceRows.InitialRegistrationAccount row) {
        return new InitialRegistrationAccount(
                row.userId(),
                row.loginId(),
                row.displayName(),
                row.passwordHash(),
                AccountStatus.fromDatabase(row.accountStatusCode()),
                flag(row.deletedFlag()),
                flag(row.passwordChangeRequiredFlag()),
                row.passwordChangedAt(),
                row.temporaryPasswordExpiresAt(),
                row.securityVersion());
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
}
