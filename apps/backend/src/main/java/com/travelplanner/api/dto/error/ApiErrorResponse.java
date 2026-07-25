package com.travelplanner.api.dto.error;

import java.util.Map;

/**
 * Standard error envelope — PLAN §6.1. Every non-2xx response uses this shape.
 *
 * @param code    snake_case machine identifier; the frontend maps it to an i18n key
 * @param message English fallback for logs and development — never shown verbatim to users
 * @param details optional structured context; never stack traces or PII
 */
public record ApiErrorResponse(String code, String message, Map<String, Object> details) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, Map.of());
    }
}
