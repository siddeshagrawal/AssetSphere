package com.assetsphere.modules.common.web;

import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.CommonConstants;

import java.time.Instant;

import org.slf4j.MDC;

public record ApiResponse<T>(
        boolean success,
        T data,
        Instant timestamp,
        String correlationId
) {
    public static <T> ApiResponse<T> success(T data, ClockProvider clock) {
        return new ApiResponse<>(true, data, clock.now(), MDC.get(CommonConstants.CORRELATION_ID));
    }
}
