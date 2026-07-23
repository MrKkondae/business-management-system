package com.bms.backend.global.error;

import java.util.List;

public record ApiProblem(
        String code, String message, String traceId, List<ApiFieldError> fieldErrors) {

    public ApiProblem {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
