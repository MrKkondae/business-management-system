package com.bms.backend.system.application.authentication;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InitialRegistrationStore {

    Optional<InitialRegistrationAccount> lockAccount(String userId);

    int complete(
            InitialRegistrationAccount account,
            String newPasswordHash,
            String emailAddress,
            String mobileNumber,
            LocalDateTime completedAt);
}
