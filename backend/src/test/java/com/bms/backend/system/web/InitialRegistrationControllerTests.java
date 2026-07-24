package com.bms.backend.system.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bms.backend.common.application.authentication.InitialRegistrationResult;
import com.bms.backend.common.application.authentication.InitialRegistrationService;
import com.bms.backend.common.application.authentication.LoginSession;
import com.bms.backend.global.error.ApiErrorCode;
import com.bms.backend.global.error.ApiProblemFactory;
import com.bms.backend.global.error.ApplicationException;
import com.bms.backend.global.error.GlobalExceptionHandler;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import com.bms.backend.global.security.ApiAccessDeniedHandler;
import com.bms.backend.global.security.ApiAuthenticationEntryPoint;
import com.bms.backend.global.security.LoginSessionManager;
import com.bms.backend.global.security.SecurityConfiguration;
import com.bms.backend.global.security.SecurityProblemWriter;
import com.bms.backend.global.web.RequestTraceFilter;
import com.bms.backend.global.web.TrustedClientIpResolver;
import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import com.bms.backend.system.application.authentication.AuthenticationRole;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InitialRegistrationController.class)
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
class InitialRegistrationControllerTests {

    private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InitialRegistrationService registrationService;

    @MockitoBean
    private LoginSessionManager sessionManager;

    @MockitoBean
    private TrustedClientIpResolver clientIpResolver;

    private LoginSession limitedSession;

    @BeforeEach
    void setUp() {
        limitedSession = new LoginSession(
                "USER-01",
                "admin",
                "관리자",
                "ACCESS-01",
                new AuthenticationAuthorizationSnapshot(List.of(), List.of()),
                true,
                LocalDateTime.of(2026, 7, 23, 1, 0),
                3,
                NOW.minusSeconds(60),
                NOW.plusSeconds(300),
                NOW.minusSeconds(60));
        when(sessionManager.require(any())).thenReturn(limitedSession);
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");
    }

    @Test
    void completesRegistrationAndPromotesTheRotatedSession() throws Exception {
        var authorization = new AuthenticationAuthorizationSnapshot(
                List.of(new AuthenticationRole("ROLE-01", "시스템관리자")), List.of());
        when(registrationService.complete(any(), any(), any()))
                .thenReturn(new InitialRegistrationResult(
                        authorization,
                        LocalDateTime.of(2026, 7, 24, 1, 0),
                        4,
                        NOW));

        mockMvc.perform(post("/users/me/initial-registration")
                        .with(user("USER-01"))
                        .with(csrf().asHeader())
                        .header("User-Agent", "JUnit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword": "River!Glass82",
                                  "newPasswordConfirmation": "River!Glass82",
                                  "emailAddress": "admin@example.com",
                                  "mobileNumber": "010-1234-5678"
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(sessionManager).promote(
                org.mockito.ArgumentMatchers.argThat(session ->
                        !session.passwordChangeRequired()
                                && session.securityVersion() == 4
                                && session.authorization().equals(authorization)
                                && session.lastUserActivityAt().equals(NOW)
                                && session.reauthenticatedAt().equals(NOW)
                                && session.absoluteExpiresAt()
                                        .equals(NOW.plusSeconds(8 * 60 * 60))),
                any(),
                any());
    }

    @Test
    void requiresCsrfAndValidRequestFields() throws Exception {
        String validBody = """
                {
                  "newPassword": "River!Glass82",
                  "newPasswordConfirmation": "River!Glass82"
                }
                """;
        mockMvc.perform(post("/users/me/initial-registration")
                        .with(user("USER-01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));

        mockMvc.perform(post("/users/me/initial-registration")
                        .with(user("USER-01"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword": "short",
                                  "newPasswordConfirmation": "short",
                                  "emailAddress": "invalid",
                                  "mobileNumber": "not-a-phone"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }

    @Test
    void invalidCredentialBaselineExpiresTheLimitedSession() throws Exception {
        when(registrationService.complete(any(), any(), any()))
                .thenThrow(new ApplicationException(
                        ApiErrorCode.AUTH_AUTHENTICATION_REQUIRED));

        mockMvc.perform(post("/users/me/initial-registration")
                        .with(user("USER-01"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword": "River!Glass82",
                                  "newPasswordConfirmation": "River!Glass82"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"));

        verify(sessionManager).invalidate(any(), any());
    }
}
