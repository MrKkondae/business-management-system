package com.bms.backend.global.security;

import com.bms.backend.global.error.ApiErrorCode;
import com.bms.backend.global.error.ApiProblem;
import com.bms.backend.global.error.ApiProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityProblemWriter {

    private final ObjectMapper objectMapper;
    private final ApiProblemFactory problemFactory;

    public SecurityProblemWriter(ObjectMapper objectMapper, ApiProblemFactory problemFactory) {
        this.objectMapper = objectMapper;
        this.problemFactory = problemFactory;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ApiErrorCode errorCode)
            throws IOException {
        ApiProblem problem = problemFactory.create(errorCode, request);
        response.setStatus(errorCode.status().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
