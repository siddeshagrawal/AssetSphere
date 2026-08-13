package com.assetsphere.modules.common.web;

import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.CommonConstants;
import com.assetsphere.modules.common.exception.AuthenticationFailedException;
import com.assetsphere.modules.common.exception.AuthorizationDeniedException;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.exception.ConflictException;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.common.exception.PayloadTooLargeException;
import com.assetsphere.modules.common.exception.RateLimitExceededException;
import com.assetsphere.modules.common.exception.QuotaExceededException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ClockProvider clock;

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), List.of());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ResponseEntity<ErrorResponse> businessRule(BusinessRuleViolationException exception) {
        return response(HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION", exception.getMessage(), List.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ErrorResponse> conflict(ConflictException exception) {
        return response(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ErrorResponse> invalidRequest(InvalidRequestException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), List.of());
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ResponseEntity<ErrorResponse> payloadTooLarge(PayloadTooLargeException exception) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", exception.getMessage(), List.of());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ErrorResponse> rateLimited(RateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(exception.getRetryAfterSeconds()))
                .body(new ErrorResponse("RATE_LIMITED", exception.getMessage(), HttpStatus.TOO_MANY_REQUESTS.value(),
                        clock.now(), MDC.get("correlationId"), List.of()));
    }

    @ExceptionHandler(QuotaExceededException.class)
    ResponseEntity<ErrorResponse> quotaExceeded(QuotaExceededException exception) {
        return response(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED", exception.getMessage(), List.of());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ResponseEntity<ErrorResponse> unavailable(ServiceUnavailableException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception) {
        var violations = exception.getBindingResult().getFieldErrors().stream().map(this::toViolation).toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed",
                violations);
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestPartException.class})
    ResponseEntity<ErrorResponse> missingRequestInput(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "A required request input is missing", List.of());
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
        log.error("Unhandled request failure method={} uri={} correlationId={}", request.getMethod(),
                request.getRequestURI(), MDC.get(CommonConstants.CORRELATION_ID), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", List.of());
    }

    private ErrorResponse.FieldViolation toViolation(FieldError error) {
        return new ErrorResponse.FieldViolation(error.getField(), error.getDefaultMessage());
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message,
                                                   List<ErrorResponse.FieldViolation> violations) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, status.value(),
                clock.now(), MDC.get("correlationId"), violations));
    }
}
