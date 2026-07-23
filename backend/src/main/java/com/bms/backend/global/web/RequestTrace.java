package com.bms.backend.global.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

public final class RequestTrace {

    public static final String ATTRIBUTE_NAME = RequestTrace.class.getName() + ".traceId";
    public static final String MDC_NAME = "traceId";
    public static final String RESPONSE_HEADER = "X-Trace-Id";

    private RequestTrace() {}

    public static String current(HttpServletRequest request) {
        Object requestValue = request.getAttribute(ATTRIBUTE_NAME);
        if (requestValue instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String mdcValue = MDC.get(MDC_NAME);
        return mdcValue == null ? "" : mdcValue;
    }
}
