package com.assetsphere.modules.common.web;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        int status,
        Instant timestamp,
        String correlationId,
        List<FieldViolation> violations
) {
    public record FieldViolation(String field, String message) {
    }
}
