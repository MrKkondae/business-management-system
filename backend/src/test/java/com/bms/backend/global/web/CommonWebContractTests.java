package com.bms.backend.global.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bms.backend.global.error.ApiProblemFactory;
import com.bms.backend.global.error.GlobalExceptionHandler;
import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import com.bms.backend.global.security.ApiAccessDeniedHandler;
import com.bms.backend.global.security.ApiAuthenticationEntryPoint;
import com.bms.backend.global.security.SecurityConfiguration;
import com.bms.backend.global.security.SecurityProblemWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CommonWebTestController.class)
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
class CommonWebContractTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedRequestReturnsCommonAuthenticationProblemAndSecurityHeaders()
            throws Exception {
        mockMvc.perform(get("/test/protected").secure(true))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json;charset=UTF-8"))
                .andExpect(header().string("X-Trace-Id", matchesPattern("[0-9A-HJKMNP-TV-Z]{26}")))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "same-origin"))
                .andExpect(header().string(
                        "Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                .andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.traceId", matchesPattern("[0-9A-HJKMNP-TV-Z]{26}")))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void publicLoginStillRequiresCsrfHeader() throws Exception {
        mockMvc.perform(post("/auth/login"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));

        mockMvc.perform(post("/auth/login").with(csrf().asHeader()))
                .andExpect(status().isNoContent());
    }

    @Test
    void csrfEndpointIsPublicAndReturnsHeaderContract() throws Exception {
        mockMvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void corsIsNotEnabledForArbitraryOrigins() throws Exception {
        mockMvc.perform(get("/auth/csrf").header("Origin", "https://attacker.example"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void beanValidationReturnsSafeFieldErrorWithoutRejectedValue() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .with(user("test-user"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("필수 입력값입니다."));
    }

    @Test
    void applicationExceptionUsesCatalogMessage() throws Exception {
        mockMvc.perform(get("/test/conflict").with(user("test-user")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("현재 상태에서는 처리할 수 없습니다."));
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalDetail() throws Exception {
        mockMvc.perform(get("/test/internal-error").with(user("test-user")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("일시적인 오류가 발생했습니다."))
                .andExpect(content().string(not(containsString("sensitive internal detail"))));
    }
}
