package com.assetsphere.modules.common;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(String code, String message, int status, Instant timestamp, String correlationId,
                            List<FieldViolation> violations) implements BaseResponse {
    public record FieldViolation(String field, String message) {
    }
}
