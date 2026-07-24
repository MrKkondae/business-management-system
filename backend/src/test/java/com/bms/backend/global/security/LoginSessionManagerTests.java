package com.bms.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.bms.backend.common.application.authentication.LoginSession;
import com.bms.backend.system.application.authentication.AuthenticationAuthorizationSnapshot;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class LoginSessionManagerTests {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rotatesThePreLoginSessionIdAndPersistsOnlyTheSecurityContext() {
        LoginSessionManager manager = new LoginSessionManager(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String preLoginSessionId = request.getSession(true).getId();

        manager.establish(session(), request, response);

        assertThat(request.getSession(false).getId()).isNotEqualTo(preLoginSessionId);
        assertThat(request.getSession(false).getMaxInactiveInterval()).isEqualTo(86_400);
        Object stored = request.getSession(false).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(stored).isInstanceOf(SecurityContext.class);
        SecurityContext context = (SecurityContext) stored;
        assertThat(context.getAuthentication().getPrincipal())
                .isEqualTo(new LoginSessionPrincipal(session()));
        assertThat(context.getAuthentication().getCredentials()).isNull();
    }

    @Test
    void invalidationExpiresTheSessionCookie() {
        LoginSessionManager manager = new LoginSessionManager(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager.invalidate(request, response);

        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(value -> value.contains("BMSSESSION=")
                        && value.contains("Max-Age=0")
                        && value.contains("HttpOnly")
                        && value.contains("SameSite=Lax"));
    }

    @Test
    void promotionRotatesSessionIdAndRemovesThePreviousCsrfToken() {
        LoginSessionManager manager = new LoginSessionManager(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        var httpSession = request.getSession(true);
        String previousId = httpSession.getId();
        String csrfAttribute =
                "org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN";
        httpSession.setAttribute(csrfAttribute, "old-token");

        manager.promote(session(), request, response);

        assertThat(request.getSession(false).getId()).isNotEqualTo(previousId);
        assertThat(request.getSession(false).getAttribute(csrfAttribute)).isNull();
        assertThat(request.getSession(false).getAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .isInstanceOf(SecurityContext.class);
    }

    private LoginSession session() {
        return new LoginSession(
                "USER-01",
                "admin",
                "관리자",
                "ACCESS-01",
                new AuthenticationAuthorizationSnapshot(List.of(), List.of()),
                false,
                LocalDateTime.of(2026, 7, 22, 9, 0),
                3,
                Instant.parse("2026-07-23T09:00:00Z"),
                Instant.parse("2026-07-23T17:00:00Z"),
                Instant.parse("2026-07-23T09:00:00Z"));
    }
}
