package com.bms.backend.global.security;

import com.bms.backend.global.error.ApiErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityProblemWriter problemWriter;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        ApiErrorCode errorCode = accessDeniedException instanceof CsrfException
                ? ApiErrorCode.AUTH_CSRF_INVALID
                : ApiErrorCode.AUTH_FORBIDDEN;
        problemWriter.write(request, response, errorCode);
    }
}
