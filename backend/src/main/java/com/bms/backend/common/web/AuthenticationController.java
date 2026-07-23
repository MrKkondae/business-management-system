package com.bms.backend.common.web;

import com.bms.backend.common.application.authentication.LoginAttemptContext;
import com.bms.backend.common.application.authentication.LoginAuthenticationResult;
import com.bms.backend.common.application.authentication.LoginAuthenticationService;
import com.bms.backend.common.application.authentication.LoginSession;
import com.bms.backend.common.application.authentication.ReauthenticationResult;
import com.bms.backend.common.application.authentication.ReauthenticationService;
import com.bms.backend.global.error.ApiErrorCode;
import com.bms.backend.global.error.ApiProblem;
import com.bms.backend.global.error.ApiProblemFactory;
import com.bms.backend.global.security.LoginSessionManager;
import com.bms.backend.global.web.RequestTrace;
import com.bms.backend.global.web.TrustedClientIpResolver;
import com.bms.backend.system.application.authentication.AuthenticationAuditStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private final LoginAuthenticationService authenticationService;
    private final LoginSessionManager sessionManager;
    private final ReauthenticationService reauthenticationService;
    private final TrustedClientIpResolver clientIpResolver;
    private final AuthenticationAuditStore auditStore;
    private final ApiProblemFactory problemFactory;
    private final Clock clock;

    public AuthenticationController(
            LoginAuthenticationService authenticationService,
            LoginSessionManager sessionManager,
            ReauthenticationService reauthenticationService,
            TrustedClientIpResolver clientIpResolver,
            AuthenticationAuditStore auditStore,
            ApiProblemFactory problemFactory,
            Clock clock) {
        this.authenticationService = authenticationService;
        this.sessionManager = sessionManager;
        this.reauthenticationService = reauthenticationService;
        this.clientIpResolver = clientIpResolver;
        this.auditStore = auditStore;
        this.problemFactory = problemFactory;
        this.clock = clock;
    }

    @GetMapping("/csrf")
    ResponseEntity<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken()));
    }

    @PostMapping("/login")
    ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        LoginAttemptContext attemptContext = attemptContext(request);
        LoginAuthenticationResult result =
                authenticationService.authenticate(body.loginId(), body.password(), attemptContext);
        if (result.status() == LoginAuthenticationResult.Status.RATE_LIMITED) {
            ApiProblem problem =
                    problemFactory.create(ApiErrorCode.AUTH_TOO_MANY_ATTEMPTS, request);
            return ResponseEntity.status(ApiErrorCode.AUTH_TOO_MANY_ATTEMPTS.status())
                    .header(HttpHeaders.RETRY_AFTER, Integer.toString(result.retryAfterSeconds()))
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .cacheControl(CacheControl.noStore())
                    .body(problem);
        }
        if (result.status() == LoginAuthenticationResult.Status.FAILED) {
            ApiProblem problem = problemFactory.create(ApiErrorCode.AUTH_LOGIN_FAILED, request);
            return ResponseEntity.status(ApiErrorCode.AUTH_LOGIN_FAILED.status())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .cacheControl(CacheControl.noStore())
                    .body(problem);
        }

        LoginSession loginSession = LoginSession.from(result.authenticatedLogin());
        sessionManager.establish(loginSession, request, response);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CurrentUserResponse.from(loginSession));
    }

    @GetMapping("/me")
    ResponseEntity<CurrentUserResponse> me(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CurrentUserResponse.from(sessionManager.require(authentication)));
    }

    @PostMapping("/activity")
    ResponseEntity<Void> activity(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        LoginSession current = sessionManager.require(authentication);
        sessionManager.replace(current.withActivityAt(clock.instant()), request, response);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        LoginSession current = sessionManager.require(authentication);
        try {
            auditStore.recordLogout(
                    current.accessLogId(),
                    current.userId(),
                    "MANUAL",
                    LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        } finally {
            sessionManager.invalidate(request, response);
        }
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PostMapping("/reauthenticate")
    ResponseEntity<?> reauthenticate(
            @Valid @RequestBody ReauthenticateRequest body,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        LoginSession current = sessionManager.require(authentication);
        ReauthenticationResult result = reauthenticationService.reauthenticate(
                current, body.password(), attemptContext(request));
        if (result.status() == ReauthenticationResult.Status.RATE_LIMITED) {
            ApiProblem problem =
                    problemFactory.create(ApiErrorCode.AUTH_TOO_MANY_ATTEMPTS, request);
            return ResponseEntity.status(ApiErrorCode.AUTH_TOO_MANY_ATTEMPTS.status())
                    .header(HttpHeaders.RETRY_AFTER, Integer.toString(result.retryAfterSeconds()))
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .cacheControl(CacheControl.noStore())
                    .body(problem);
        }
        if (result.status() == ReauthenticationResult.Status.FAILED) {
            ApiProblem problem =
                    problemFactory.create(ApiErrorCode.AUTH_REAUTHENTICATION_FAILED, request);
            return ResponseEntity.status(ApiErrorCode.AUTH_REAUTHENTICATION_FAILED.status())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .cacheControl(CacheControl.noStore())
                    .body(problem);
        }

        sessionManager.replace(
                current.withReauthenticatedAt(result.reauthenticatedAt()), request, response);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private LoginAttemptContext attemptContext(HttpServletRequest request) {
        return new LoginAttemptContext(
                RequestTrace.current(request),
                clientIpResolver.resolve(request),
                request.getHeader(HttpHeaders.USER_AGENT));
    }

    record LoginRequest(
            @NotBlank @Size(max = 100) String loginId,
            @NotBlank @Size(max = 256) String password) {}

    record ReauthenticateRequest(@NotBlank @Size(max = 256) String password) {}

    record CsrfTokenResponse(String headerName, String token) {}

    record CurrentUserResponse(
            AuthenticatedUser user,
            List<AuthenticatedRole> roles,
            List<AuthorizedMenu> menus,
            boolean passwordChangeRequired,
            int idleTimeoutSeconds,
            java.time.Instant absoluteSessionExpiresAt) {

        static CurrentUserResponse from(LoginSession session) {
            return new CurrentUserResponse(
                    new AuthenticatedUser(
                            session.userId(), session.loginId(), session.displayName()),
                    session.authorization().roles().stream()
                            .map(role -> new AuthenticatedRole(role.roleId(), role.roleName()))
                            .toList(),
                    session.authorization().menus().stream()
                            .map(menu -> new AuthorizedMenu(
                                    menu.menuId(),
                                    menu.parentMenuId(),
                                    menu.menuName(),
                                    menu.menuUrl(),
                                    menu.sortOrder()))
                            .toList(),
                    session.passwordChangeRequired(),
                    session.idleTimeoutSeconds(),
                    session.absoluteExpiresAt());
        }
    }

    record AuthenticatedUser(String userId, String loginId, String displayName) {}

    record AuthenticatedRole(String roleId, String roleName) {}

    record AuthorizedMenu(
            String menuId,
            String parentMenuId,
            String menuName,
            String menuUrl,
            int sortOrder) {}
}
