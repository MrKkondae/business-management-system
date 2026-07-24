package com.bms.backend.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bms.backend.common.application.authentication.LoginSession;
import com.bms.backend.global.error.ApiErrorCode;
import com.bms.backend.system.application.authentication.AccountStatus;
import com.bms.backend.system.application.authentication.AuthenticationAuditStore;
import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import com.bms.backend.system.application.authentication.AuthenticationSessionState;
import com.bms.backend.system.application.authentication.AuthenticationUserQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class LoginSessionValidationFilterTests {

    private static final Instant NOW = Instant.parse("2026-07-23T09:10:00Z");

    private AuthenticationUserQuery userQuery;
    private AuthenticationAuditStore auditStore;
    private LoginSessionManager sessionManager;
    private SecurityProblemWriter problemWriter;
    private OncePerRequestFilter filter;

    @BeforeEach
    void setUp() {
        userQuery = org.mockito.Mockito.mock(AuthenticationUserQuery.class);
        auditStore = org.mockito.Mockito.mock(AuthenticationAuditStore.class);
        sessionManager = org.mockito.Mockito.mock(LoginSessionManager.class);
        problemWriter = org.mockito.Mockito.mock(SecurityProblemWriter.class);
        filter = new LoginSessionValidationFilter(
                userQuery,
                auditStore,
                sessionManager,
                problemWriter,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validSessionContinuesAfterDatabaseSecurityVersionCheck() throws Exception {
        LoginSession session = sessionAt(Instant.parse("2026-07-23T09:00:00Z"), false);
        authenticate(session);
        when(userQuery.findSessionState("USER-01")).thenReturn(Optional.of(state(false)));
        var request = request("GET", "/auth/me");
        var chain = org.mockito.Mockito.mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verify(sessionManager, never()).invalidate(any(), any());
    }

    @Test
    void idleExpiredSessionClosesAccessLogAndReturnsAuthenticationRequired() throws Exception {
        LoginSession session = sessionAt(Instant.parse("2026-07-23T08:54:59Z"), true);
        authenticate(session);
        var request = request("GET", "/auth/me");
        var response = new MockHttpServletResponse();
        var chain = org.mockito.Mockito.mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(auditStore).recordLogout(
                "ACCESS-01",
                "USER-01",
                "TIMEOUT",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(sessionManager).invalidate(request, response);
        verify(problemWriter).write(
                request, response, ApiErrorCode.AUTH_AUTHENTICATION_REQUIRED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void limitedSessionRejectsNonAllowlistedApi() throws Exception {
        LoginSession session = sessionAt(Instant.parse("2026-07-23T09:05:00Z"), true);
        authenticate(session);
        when(userQuery.findSessionState("USER-01")).thenReturn(Optional.of(state(true)));
        var request = request("GET", "/users");
        var response = new MockHttpServletResponse();
        var chain = org.mockito.Mockito.mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(problemWriter).write(
                request, response, ApiErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void limitedSessionAllowsOnlyTheInitialRegistrationCommand() throws Exception {
        LoginSession session = sessionAt(Instant.parse("2026-07-23T09:05:00Z"), true);
        authenticate(session);
        when(userQuery.findSessionState("USER-01")).thenReturn(Optional.of(state(true)));
        var request = request("POST", "/users/me/initial-registration");
        var chain = org.mockito.Mockito.mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verify(problemWriter, never()).write(any(), any(), any());
    }

    private LoginSession sessionAt(Instant lastActivity, boolean limited) {
        return new LoginSession(
                "USER-01",
                "admin",
                "관리자",
                "ACCESS-01",
                new AuthenticationAuthorizationSnapshot(List.of(), List.of()),
                limited,
                LocalDateTime.of(2026, 7, 22, 9, 0),
                3,
                lastActivity,
                Instant.parse("2026-07-23T17:00:00Z"),
                lastActivity);
    }

    private AuthenticationSessionState state(boolean limited) {
        return new AuthenticationSessionState(
                "USER-01",
                AccountStatus.ACTIVE,
                false,
                3,
                limited,
                LocalDateTime.of(2026, 7, 22, 9, 0));
    }

    private void authenticate(LoginSession session) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new LoginSessionPrincipal(session), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private MockHttpServletRequest request(String method, String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, servletPath);
        request.setServletPath(servletPath);
        return request;
    }
}
