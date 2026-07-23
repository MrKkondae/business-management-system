package com.bms.backend.global.web;

import com.bms.backend.global.persistence.MonotonicUlidGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequestTraceFilter extends OncePerRequestFilter {

    private final MonotonicUlidGenerator ulidGenerator;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = ulidGenerator.next();
        request.setAttribute(RequestTrace.ATTRIBUTE_NAME, traceId);
        response.setHeader(RequestTrace.RESPONSE_HEADER, traceId);
        MDC.put(RequestTrace.MDC_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestTrace.MDC_NAME);
        }
    }
}
