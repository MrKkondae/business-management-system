package com.bms.backend.global.error;

import com.bms.backend.global.web.RequestTrace;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApiProblemFactory {

    public ApiProblem create(ApiErrorCode errorCode, HttpServletRequest request) {
        return create(errorCode, request, List.of());
    }

    public ApiProblem create(
            ApiErrorCode errorCode,
            HttpServletRequest request,
            List<ApiFieldError> fieldErrors) {
        return new ApiProblem(
                errorCode.name(),
                errorCode.userMessage(),
                RequestTrace.current(request),
                fieldErrors);
    }
}
