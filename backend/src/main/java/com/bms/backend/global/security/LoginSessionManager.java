package com.bms.backend.global.security;

import com.bms.backend.common.application.authentication.LoginSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class LoginSessionManager {

    private static final String CSRF_SESSION_ATTRIBUTE =
            "org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN";

    private final HttpSessionSecurityContextRepository repository =
            new HttpSessionSecurityContextRepository();
    private final boolean secureCookie;

    public LoginSessionManager(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    public void establish(
            LoginSession loginSession,
            HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession httpSession = request.getSession(true);
        request.changeSessionId();
        httpSession.removeAttribute(CSRF_SESSION_ATTRIBUTE);
        // Business expiry is checked by LoginSessionValidationFilter. Keep the container
        // session longer so the filter can close the access log on the next request.
        httpSession.setMaxInactiveInterval((int) java.time.Duration.ofHours(24).toSeconds());
        save(loginSession, request, response);
    }

    public void promote(
            LoginSession loginSession,
            HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession httpSession = request.getSession(false);
        if (httpSession == null) {
            throw new IllegalStateException("AUTH_HTTP_SESSION_REQUIRED");
        }
        request.changeSessionId();
        httpSession.removeAttribute(CSRF_SESSION_ATTRIBUTE);
        save(loginSession, request, response);
    }

    public void replace(
            LoginSession loginSession,
            HttpServletRequest request,
            HttpServletResponse response) {
        save(loginSession, request, response);
    }

    public LoginSession require(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof LoginSessionPrincipal principal)) {
            throw new IllegalStateException("AUTH_LOGIN_SESSION_REQUIRED");
        }
        return principal.session();
    }

    public void invalidate(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        ResponseCookie expiredCookie = ResponseCookie.from("BMSSESSION", "")
                .path("/")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
    }

    private void save(
            LoginSession loginSession,
            HttpServletRequest request,
            HttpServletResponse response) {
        List<SimpleGrantedAuthority> authorities = loginSession.authorization().roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.roleId()))
                .toList();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                new LoginSessionPrincipal(loginSession), null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
    }
}
