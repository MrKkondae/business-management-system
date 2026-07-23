package com.bms.backend.common.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bms.backend.common.application.authentication.AuthenticatedLogin;
import com.bms.backend.common.application.authentication.LoginAuthenticationResult;
import com.bms.backend.common.application.authentication.LoginAuthenticationService;
import com.bms.backend.common.application.authentication.LoginSession;
import com.bms.backend.common.application.authentication.ReauthenticationResult;
import com.bms.backend.common.application.authentication.ReauthenticationService;
import com.bms.backend.global.error.ApiProblemFactory;
import com.bms.backend.global.error.GlobalExceptionHandler;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import com.bms.backend.global.security.ApiAccessDeniedHandler;
import com.bms.backend.global.security.ApiAuthenticationEntryPoint;
import com.bms.backend.global.security.LoginSessionManager;
import com.bms.backend.global.security.SecurityConfiguration;
import com.bms.backend.global.security.SecurityProblemWriter;
import com.bms.backend.global.web.RequestTraceFilter;
import com.bms.backend.global.web.TrustedClientIpResolver;
import com.bms.backend.system.application.authentication.AuthenticationAuditStore;
import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import com.bms.backend.system.application.authentication.AuthenticationMenu;
import com.bms.backend.system.application.authentication.AuthenticationRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthenticationController.class)
@Import({
    SecurityConfiguration.class,
    ApiAuthenticationEntryPoint.class,
    ApiAccessDeniedHandler.class,
    SecurityProblemWriter.class,
    ApiProblemFactory.class,
    GlobalExceptionHandler.class,
    RequestTraceFilter.class,
    MonotonicUlidGenerator.class
})
class AuthenticationControllerTests {

    private static final Instant NOW = Instant.parse("2026-07-23T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginAuthenticationService authenticationService;

    @MockitoBean
    private LoginSessionManager sessionManager;

    @MockitoBean
    private ReauthenticationService reauthenticationService;

    @MockitoBean
    private TrustedClientIpResolver clientIpResolver;

    @MockitoBean
    private AuthenticationAuditStore auditStore;

    @MockitoBean
    private Clock clock;

    private LoginSession loginSession;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");
        loginSession = LoginSession.from(authenticatedLogin(false));
    }

    @Test
    void successfulLoginCreatesSessionAndReturnsCurrentUserContract() throws Exception {
        when(authenticationService.authenticate(any(), any(), any()))
                .thenReturn(LoginAuthenticationResult.success(authenticatedLogin(false)));

        mockMvc.perform(post("/auth/login")
                        .with(csrf().asHeader())
                        .header("User-Agent", "JUnit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Trace-Id", matchesPattern("[0-9A-HJKMNP-TV-Z]{26}")))
                .andExpect(jsonPath("$.user.userId").value("USER-01"))
                .andExpect(jsonPath("$.user.loginId").value("admin"))
                .andExpect(jsonPath("$.roles[0].roleId").value("ROLE-01"))
                .andExpect(jsonPath("$.menus[0].menuId").value("MENU-01"))
                .andExpect(jsonPath("$.passwordChangeRequired").value(false))
                .andExpect(jsonPath("$.idleTimeoutSeconds").value(900))
                .andExpect(jsonPath("$.absoluteSessionExpiresAt")
                        .value("2026-07-23T17:00:00Z"));

        verify(sessionManager).establish(any(), any(), any());
    }

    @Test
    void failedLoginReturnsOneGenericUnauthorizedProblem() throws Exception {
        when(authenticationService.authenticate(any(), any(), any()))
                .thenReturn(LoginAuthenticationResult.failed());

        mockMvc.perform(post("/auth/login")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"unknown\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_FAILED"));
    }

    @Test
    void rateLimitedLoginReturnsRetryAfterWithoutCredentialDetail() throws Exception {
        when(authenticationService.authenticate(any(), any(), any()))
                .thenReturn(LoginAuthenticationResult.rateLimited(47));

        mockMvc.perform(post("/auth/login")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "47"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("AUTH_TOO_MANY_ATTEMPTS"));
    }

    @Test
    void loginRequiresJsonValidationAndCsrf() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));

        mockMvc.perform(post("/auth/login")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }

    @Test
    void currentUserActivityAndLogoutUseTheSessionSnapshot() throws Exception {
        when(sessionManager.require(any())).thenReturn(loginSession);

        mockMvc.perform(get("/auth/me").with(user("USER-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userId").value("USER-01"));

        mockMvc.perform(post("/auth/activity")
                        .with(user("USER-01"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        verify(sessionManager).replace(any(), any(), any());

        mockMvc.perform(post("/auth/logout")
                        .with(user("USER-01"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        verify(auditStore).recordLogout(
                "ACCESS-01",
                "USER-01",
                "MANUAL",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(sessionManager).invalidate(any(), any());
    }

    @Test
    void reauthenticationUpdatesOnlyTheCurrentSessionTimestamp() throws Exception {
        when(sessionManager.require(any())).thenReturn(loginSession);
        when(reauthenticationService.reauthenticate(any(), any(), any()))
                .thenReturn(ReauthenticationResult.success(NOW.plusSeconds(30)));

        mockMvc.perform(post("/auth/reauthenticate")
                        .with(user("USER-01"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"current-secret\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(sessionManager).replace(
                org.mockito.ArgumentMatchers.argThat(session ->
                        session.reauthenticatedAt().equals(NOW.plusSeconds(30))
                                && session.absoluteExpiresAt()
                                        .equals(loginSession.absoluteExpiresAt())),
                any(),
                any());
    }

    @Test
    void failedAndRateLimitedReauthenticationUseSafeErrors() throws Exception {
        when(sessionManager.require(any())).thenReturn(loginSession);
        when(reauthenticationService.reauthenticate(any(), any(), any()))
                .thenReturn(ReauthenticationResult.failed());

        mockMvc.perform(post("/auth/reauthenticate")
                        .with(user("USER-01"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_REAUTHENTICATION_FAILED"));

        when(reauthenticationService.reauthenticate(any(), any(), any()))
                .thenReturn(ReauthenticationResult.rateLimited(60));
        mockMvc.perform(post("/auth/reauthenticate")
                        .with(user("USER-01"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("AUTH_TOO_MANY_ATTEMPTS"));
    }

    private AuthenticatedLogin authenticatedLogin(boolean passwordChangeRequired) {
        return new AuthenticatedLogin(
                "USER-01",
                "admin",
                "관리자",
                "ACCESS-01",
                new AuthenticationAuthorizationSnapshot(
                        List.of(new AuthenticationRole("ROLE-01", "시스템관리자")),
                        passwordChangeRequired
                                ? List.of()
                                : List.of(new AuthenticationMenu(
                                        "MENU-01", null, "사용자 관리", "/users", 10))),
                passwordChangeRequired,
                passwordChangeRequired ? LocalDateTime.of(2026, 7, 23, 9, 20) : null,
                LocalDateTime.of(2026, 7, 22, 9, 0),
                3,
                LocalDateTime.of(2026, 7, 23, 9, 0));
    }
}
