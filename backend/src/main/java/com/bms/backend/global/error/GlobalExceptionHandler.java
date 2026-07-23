package com.bms.backend.global.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiProblemFactory problemFactory;

    public GlobalExceptionHandler(ApiProblemFactory problemFactory) {
        this.problemFactory = problemFactory;
    }

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<ApiProblem> handleApplicationException(
            ApplicationException exception, HttpServletRequest request) {
        ApiErrorCode errorCode = exception.errorCode();
        logExpected(errorCode);
        return response(errorCode, problemFactory.create(errorCode, request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toApiFieldError)
                .toList();
        return validationResponse(request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiProblem> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = exception.getConstraintViolations().stream()
                .map(this::toApiFieldError)
                .toList();
        return validationResponse(request, fieldErrors);
    }

    @ExceptionHandler({
        HandlerMethodValidationException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMediaTypeNotSupportedException.class,
        HttpRequestMethodNotSupportedException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<ApiProblem> handleInvalidRequest(
            Exception exception, HttpServletRequest request) {
        return validationResponse(request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiProblem> handleNotFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        ApiErrorCode errorCode = ApiErrorCode.COMMON_RESOURCE_NOT_FOUND;
        logExpected(errorCode);
        return response(errorCode, problemFactory.create(errorCode, request));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiProblem> handleConcurrentModification(
            OptimisticLockingFailureException exception, HttpServletRequest request) {
        ApiErrorCode errorCode = ApiErrorCode.COMMON_CONCURRENT_MODIFICATION;
        logExpected(errorCode);
        return response(errorCode, problemFactory.create(errorCode, request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiProblem> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        ApiErrorCode errorCode = ApiErrorCode.COMMON_INTERNAL_ERROR;
        log.error("Unhandled request failure. exceptionType={}", exception.getClass().getName());
        return response(errorCode, problemFactory.create(errorCode, request));
    }

    private ResponseEntity<ApiProblem> validationResponse(
            HttpServletRequest request, List<ApiFieldError> fieldErrors) {
        ApiErrorCode errorCode = ApiErrorCode.COMMON_VALIDATION_FAILED;
        logExpected(errorCode);
        return response(errorCode, problemFactory.create(errorCode, request, fieldErrors));
    }

    private ResponseEntity<ApiProblem> response(
            ApiErrorCode errorCode, ApiProblem problem) {
        return ResponseEntity.status(errorCode.status())
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(problem);
    }

    private ApiFieldError toApiFieldError(FieldError fieldError) {
        String reason = reason(fieldError.getCode());
        return new ApiFieldError(fieldError.getField(), reason, fieldMessage(reason));
    }

    private ApiFieldError toApiFieldError(ConstraintViolation<?> violation) {
        String field = "request";
        for (var node : violation.getPropertyPath()) {
            field = node.getName();
        }
        String reason = reason(
                violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
        return new ApiFieldError(field, reason, fieldMessage(reason));
    }

    private String reason(String validationCode) {
        if (validationCode == null) {
            return "INVALID";
        }
        return switch (validationCode.toUpperCase(Locale.ROOT)) {
            case "NOTNULL", "NOTBLANK", "NOTEMPTY" -> "REQUIRED";
            case "SIZE", "LENGTH" -> "SIZE";
            case "PATTERN", "EMAIL", "TYPEMISMATCH" -> "FORMAT";
            default -> "INVALID";
        };
    }

    private String fieldMessage(String reason) {
        return switch (reason) {
            case "REQUIRED" -> "필수 입력값입니다.";
            case "SIZE" -> "입력값의 길이를 확인해 주세요.";
            case "FORMAT" -> "입력 형식을 확인해 주세요.";
            default -> "입력값을 확인해 주세요.";
        };
    }

    private void logExpected(ApiErrorCode errorCode) {
        switch (errorCode.diagnosticLevel()) {
            case INFO -> log.info("Request rejected. code={}", errorCode.name());
            case WARN -> log.warn("Request rejected. code={}", errorCode.name());
            case ERROR -> log.error("Request failed. code={}", errorCode.name());
        }
    }
}
