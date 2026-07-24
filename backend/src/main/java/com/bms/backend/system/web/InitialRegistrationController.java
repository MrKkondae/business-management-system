package com.bms.backend.system.web;

import com.bms.backend.common.application.authentication.InitialRegistrationCommand;
import com.bms.backend.common.application.authentication.InitialRegistrationResult;
import com.bms.backend.common.application.authentication.InitialRegistrationService;
import com.bms.backend.common.application.authentication.LoginAttemptContext;
import com.bms.backend.common.application.authentication.LoginSession;
import com.bms.backend.global.error.ApiErrorCode;
import com.bms.backend.global.error.ApplicationException;
import com.bms.backend.global.security.LoginSessionManager;
import com.bms.backend.global.web.RequestTrace;
import com.bms.backend.global.web.TrustedClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
public class InitialRegistrationController {

    private final InitialRegistrationService registrationService;
    private final LoginSessionManager sessionManager;
    private final TrustedClientIpResolver clientIpResolver;

    public InitialRegistrationController(
            InitialRegistrationService registrationService,
            LoginSessionManager sessionManager,
            TrustedClientIpResolver clientIpResolver) {
        this.registrationService = registrationService;
        this.sessionManager = sessionManager;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/initial-registration")
    ResponseEntity<Void> complete(
            @Valid @RequestBody InitialRegistrationRequest body,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        LoginSession current = sessionManager.require(authentication);
        InitialRegistrationResult result;
        try {
            result = registrationService.complete(
                    current,
                    new InitialRegistrationCommand(
                            body.newPassword(),
                            body.newPasswordConfirmation(),
                            body.emailAddress(),
                            body.mobileNumber()),
                    new LoginAttemptContext(
                            RequestTrace.current(request),
                            clientIpResolver.resolve(request),
                            request.getHeader(HttpHeaders.USER_AGENT)));
        } catch (ApplicationException exception) {
            if (exception.errorCode() == ApiErrorCode.AUTH_AUTHENTICATION_REQUIRED) {
                sessionManager.invalidate(request, response);
            }
            throw exception;
        }

        LoginSession promoted = current.promoted(
                result.authorization(),
                result.passwordChangedAt(),
                result.securityVersion(),
                result.completedAt());
        sessionManager.promote(promoted, request, response);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    record InitialRegistrationRequest(
            @NotBlank @Size(min = 12, max = 64) String newPassword,
            @NotBlank @Size(min = 12, max = 64) String newPasswordConfirmation,
            @Email @Size(max = 100) String emailAddress,
            @Pattern(regexp = "^[0-9+() -]*$") @Size(max = 20) String mobileNumber) {

        @Override
        public String toString() {
            return "InitialRegistrationRequest[newPassword=***, "
                    + "newPasswordConfirmation=***, emailAddress=***, mobileNumber=***]";
        }
    }
}
