package com.bms.backend.global.security;

import com.bms.backend.common.application.authentication.LoginSession;
import com.bms.backend.global.error.ApiErrorCode;
import com.bms.backend.global.error.ApplicationException;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class RecentReauthenticationGuard {

    private final Clock clock;

    public RecentReauthenticationGuard(Clock clock) {
        this.clock = clock;
    }

    public void requireRecent(LoginSession session) {
        if (session.passwordChangeRequired()) {
            throw new ApplicationException(ApiErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED);
        }
        if (!session.isRecentlyReauthenticated(clock.instant())) {
            throw new ApplicationException(ApiErrorCode.AUTH_REAUTHENTICATION_REQUIRED);
        }
    }
}
