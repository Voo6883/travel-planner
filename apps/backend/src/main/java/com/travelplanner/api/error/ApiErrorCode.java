package com.travelplanner.api.error;

import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/**
 * The error catalog, in Java. Mirrors {@code api/openapi/errors.yaml} — the two are asserted
 * equal by {@code OpenApiSpecTest}, so a code can never exist on one side only.
 *
 * <p>This enum is the reason an unregistered code cannot reach a client:
 * {@code GlobalExceptionHandler} resolves every {@code DomainException} through
 * {@link #fromCode(String)} and downgrades an unknown code to {@link #INTERNAL_ERROR}. A frontend
 * that cannot translate a code shows a raw identifier to the user, so "unregistered" has to be a
 * build failure rather than a runtime surprise.
 *
 * <p>Registration procedure: see the header of {@code api/openapi/errors.yaml}.
 */
public enum ApiErrorCode {

    /** Authenticated, but not allowed to act on this resource. */
    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN),

    /** Unhandled server fault. Details never leave the logs. */
    INTERNAL_ERROR("internal_error", HttpStatus.INTERNAL_SERVER_ERROR),

    /** No resource at this path, or none owned by the caller. */
    NOT_FOUND("not_found", HttpStatus.NOT_FOUND),

    /** No valid session; the caller must sign in. */
    UNAUTHORIZED("unauthorized", HttpStatus.UNAUTHORIZED),

    /** Schema or constraint failure. {@code details.fields} maps field name to message. */
    VALIDATION_FAILED("validation_failed", HttpStatus.BAD_REQUEST),

    /** Optimistic-lock mismatch (ADR 008). {@code details.current_version} carries the truth. */
    VERSION_CONFLICT("version_conflict", HttpStatus.CONFLICT);

    private final String code;
    private final HttpStatus status;

    ApiErrorCode(String code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    /** The {@code snake_case} wire value. */
    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    /** The frontend i18n key this code resolves to (PLAN §6.1). */
    public String i18nKey() {
        return "errors." + code;
    }

    public static Optional<ApiErrorCode> fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst();
    }
}
