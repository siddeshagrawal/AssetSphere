package com.assetsphere.modules.common;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

import org.slf4j.MDC;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    private final ClockProvider clock;

    GlobalExceptionHandler(ClockProvider clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), List.of());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ResponseEntity<ErrorResponse> businessRule(BusinessRuleViolationException exception) {
        return response(HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION", exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception) {
        var violations = exception.getBindingResult().getFieldErrors().stream().map(this::toViolation).toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", violations);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ErrorResponse> unauthenticated(AuthenticationFailedException exception) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", exception.getMessage(), List.of());
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<ErrorResponse> forbidden(AuthorizationDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", exception.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure method={} uri={} correlationId={}", request.getMethod(), request.getRequestURI(), MDC.get(CommonConstants.CORRELATION_ID), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", List.of());
    }

    private ErrorResponse.FieldViolation toViolation(FieldError error) {
        return new ErrorResponse.FieldViolation(error.getField(), error.getDefaultMessage());
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message, List<ErrorResponse.FieldViolation> violations) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, status.value(), clock.now(), MDC.get("correlationId"), violations));
    }
}
