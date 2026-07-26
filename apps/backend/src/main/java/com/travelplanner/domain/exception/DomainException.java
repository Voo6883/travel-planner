package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * Base for every failure the domain can express as an API error (PLAN §4.0.2-J).
 *
 * <p>Deliberately framework-free: no Spring, no Jakarta, no HTTP status. A domain rule knows
 * <em>what</em> went wrong, not which status code an HTTP adapter chose for it. That mapping
 * lives in {@code api/error/ApiErrorCode} and is applied by {@code GlobalExceptionHandler}.
 *
 * <p>Subclasses must use a {@code code} registered in
 * {@code api/openapi/errors.yaml}. An unregistered code is downgraded to {@code internal_error}
 * by the handler rather than leaked, so forgetting to register fails loudly in tests instead of
 * quietly shipping an error the frontend cannot translate.
 */
public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final transient Map<String, Object> details;

    protected DomainException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    /** Registered {@code snake_case} identifier; the frontend maps it to an i18n key. */
    public String code() {
        return code;
    }

    /** Structured context. Never empty-checked by callers — it is never {@code null}. */
    public Map<String, Object> details() {
        return details;
    }
}
