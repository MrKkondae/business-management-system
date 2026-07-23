package com.bms.backend.global.security;

import com.bms.backend.common.application.authentication.LoginSession;
import com.bms.backend.global.error.ApiErrorCode;
import com.bms.backend.system.application.authentication.AuthenticationAuditStore;
import com.bms.backend.system.application.authentication.AuthenticationSessionState;
import com.bms.backend.system.application.authentication.AuthenticationUserQuery;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnBean({
    AuthenticationUserQuery.class,
    AuthenticationAuditStore.class
})
public class LoginSessionValidationFilter extends OncePerRequestFilter {

    private final AuthenticationUserQuery userQuery;
    private final AuthenticationAuditStore auditStore;
    private final LoginSessionManager sessionManager;
    private final SecurityProblemWriter problemWriter;
    private final Clock clock;

    public LoginSessionValidationFilter(
            AuthenticationUserQuery userQuery,
            AuthenticationAuditStore auditStore,
            LoginSessionManager sessionManager,
            SecurityProblemWriter problemWriter,
            Clock clock) {
        this.userQuery = userQuery;
        this.auditStore = auditStore;
        this.sessionManager = sessionManager;
        this.problemWriter = problemWriter;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof LoginSessionPrincipal principal)
                || publicAuthenticationPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        LoginSession session = principal.session();
        LoginSession.Expiration expiration = session.expirationAt(clock.instant());
        if (expiration != LoginSession.Expiration.NONE) {
            terminate(session, request, response, "TIMEOUT");
            problemWriter.write(
                    request, response, ApiErrorCode.AUTH_AUTHENTICATION_REQUIRED);
            return;
        }

        AuthenticationSessionState state = userQuery.findSessionState(session.userId())
                .orElse(null);
        if (state == null
                || !state.isUsableWith(session.securityVersion())
                || state.passwordChangeRequired() != session.passwordChangeRequired()
                || !Objects.equals(state.passwordChangedAt(), session.passwordChangedAt())) {
            terminate(session, request, response, "TIMEOUT");
            problemWriter.write(
                    request, response, ApiErrorCode.AUTH_AUTHENTICATION_REQUIRED);
            return;
        }

        if (session.passwordChangeRequired() && !limitedSessionPathAllowed(request)) {
            problemWriter.write(
                    request, response, ApiErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void terminate(
            LoginSession session,
            HttpServletRequest request,
            HttpServletResponse response,
            String logoutTypeCode) {
        try {
            auditStore.recordLogout(
                    session.accessLogId(),
                    session.userId(),
                    logoutTypeCode,
                    LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        } finally {
            sessionManager.invalidate(request, response);
        }
    }

    private boolean publicAuthenticationPath(HttpServletRequest request) {
        return request.getServletPath().equals("/auth/login")
                || request.getServletPath().equals("/auth/csrf");
    }

    private boolean limitedSessionPathAllowed(HttpServletRequest request) {
        String methodAndPath = request.getMethod() + " " + request.getServletPath();
        return methodAndPath.equals("GET /auth/me")
                || methodAndPath.equals("POST /auth/activity")
                || methodAndPath.equals("POST /auth/logout")
                || methodAndPath.equals("POST /users/me/initial-registration");
    }
}
