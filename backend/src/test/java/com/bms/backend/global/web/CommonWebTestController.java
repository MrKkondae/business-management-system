package com.bms.backend.global.web;

import com.bms.backend.global.error.ApiErrorCode;
import com.bms.backend.global.error.ApplicationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CommonWebTestController {

    @GetMapping("/auth/csrf")
    Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
                "headerName", csrfToken.getHeaderName(),
                "token", csrfToken.getToken());
    }

    @PostMapping("/auth/login")
    ResponseEntity<Void> login() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/test/protected")
    Map<String, Boolean> protectedResource() {
        return Map.of("ok", true);
    }

    @PostMapping("/test/validation")
    ResponseEntity<Void> validate(@Valid @RequestBody TestRequest request) {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/test/conflict")
    void conflict() {
        throw new ApplicationException(ApiErrorCode.COMMON_INVALID_STATE);
    }

    @GetMapping("/test/internal-error")
    void internalError() {
        throw new IllegalStateException("sensitive internal detail");
    }

    record TestRequest(@NotBlank String name) {}
}
