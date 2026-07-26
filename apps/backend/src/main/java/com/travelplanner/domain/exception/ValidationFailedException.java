package com.travelplanner.domain.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request data failed a constraint. Maps to {@code 400 validation_failed}.
 *
 * <p>Most validation failures arrive from Jakarta Bean Validation and are translated by
 * {@code GlobalExceptionHandler}. This exception covers the cases Bean Validation cannot see:
 * value objects with invariants, and callers that are not HTTP requests at all — an LLM tool
 * invocation, for example, must fail through the same code and the same envelope as a form post,
 * or the frontend ends up with two error vocabularies for one failure.
 */
public class ValidationFailedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "validation_failed";

    /**
     * @param fields wire field name (snake_case) → English constraint message
     */
    public ValidationFailedException(Map<String, String> fields) {
        super(CODE, "The request is not valid.", Map.of("fields", copyOf(fields)));
    }

    /** Convenience for the common single-field case. */
    public static ValidationFailedException field(String name, String message) {
        return new ValidationFailedException(Map.of(name, message));
    }

    private static Map<String, String> copyOf(Map<String, String> fields) {
        // LinkedHashMap, not Map.copyOf: field order in the response should match the order the
        // constraints were reported, so the first error a user sees is the first one that failed.
        return fields == null ? Map.of() : new LinkedHashMap<>(fields);
    }
}
