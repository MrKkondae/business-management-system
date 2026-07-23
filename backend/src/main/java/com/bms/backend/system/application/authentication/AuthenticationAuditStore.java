package com.bms.backend.system.application.authentication;

import com.bms.backend.common.application.authentication.LoginAttemptContext;
import com.bms.backend.common.application.authentication.LoginFailureReason;
import java.time.LocalDateTime;

public interface AuthenticationAuditStore {

    String recordSuccessfulLogin(
            String userId, LoginAttemptContext context, LocalDateTime occurredAt);

    void recordFailedLogin(
            String userId,
            String protectedLoginId,
            LoginFailureReason failureReason,
            LoginAttemptContext context,
            LocalDateTime occurredAt);

    void recordAccountDeactivated(
            String userId, LoginAttemptContext context, LocalDateTime occurredAt);

    void recordRateLimited(LoginAttemptContext context, LocalDateTime occurredAt);

    void recordLogout(
            String accessLogId,
            String userId,
            String logoutTypeCode,
            LocalDateTime occurredAt);

    void recordReauthentication(
            String userId,
            ReauthenticationAuditOutcome outcome,
            LoginAttemptContext context,
            LocalDateTime occurredAt);
}
