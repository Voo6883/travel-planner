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

    /** Credential accepted, but {@code user.enabled} is false (ADR 009 §1). */
    ACCOUNT_DISABLED("account_disabled", HttpStatus.FORBIDDEN),

    /**
     * Lockout window exhausted (ADR 009 §6). {@code 423 Locked} rather than {@code 429}: the
     * account is temporarily unusable, which is a different condition from a client exceeding a
     * request quota — and {@code 429} is reserved for the rate limits PLAN §4.0.9 schedules for
     * Phase 1+.
     */
    ACCOUNT_LOCKED("account_locked", HttpStatus.LOCKED),

    /**
     * The AI provider rejected the request for quota reasons (task 14).
     *
     * <p>{@code 429}, and the platform retries it with backoff before it ever gets this far — so a
     * client seeing this code has already had three attempts spent on its behalf.
     */
    AI_RATE_LIMITED("ai_rate_limited", HttpStatus.TOO_MANY_REQUESTS),

    /**
     * The model answered, but the answer failed schema validation and one repair attempt did not fix
     * it (task 14).
     *
     * <p>{@code 502} rather than {@code 500}: the fault is in an upstream response, not in this
     * application. It is deliberately a typed failure and never a partially populated object —
     * PLAN §4.1 requires "no confident result" to be a valid outcome rather than something invented
     * to fill the fields.
     */
    AI_RESPONSE_INVALID("ai_response_invalid", HttpStatus.BAD_GATEWAY),

    /**
     * The AI provider did not answer within its budget (task 14).
     *
     * <p>{@code 504}, the standard meaning of an upstream that did not answer in time. Not
     * auto-retried: the call already consumed its whole budget, and a second attempt would double
     * the wait a user is sitting through for the same outcome.
     */
    AI_TIMEOUT("ai_timeout", HttpStatus.GATEWAY_TIMEOUT),

    /**
     * The AI provider is unreachable, failing, misconfigured, or its circuit breaker is open
     * (task 14).
     *
     * <p>{@code 503}, so a client can distinguish "try again shortly" from a request that will never
     * work. ADR 007 also requires this code to be deliverable <em>inside</em> a stream, as a
     * {@code StreamError} frame, because after a {@code 200} the status line is no longer available.
     */
    AI_UNAVAILABLE("ai_unavailable", HttpStatus.SERVICE_UNAVAILABLE),

    /** Credential accepted, but the address is unconfirmed (UC-A08). */
    EMAIL_NOT_VERIFIED("email_not_verified", HttpStatus.FORBIDDEN),

    /** Authenticated, but not allowed to act on this resource. */
    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN),

    /** Unhandled server fault. Details never leave the logs. */
    INTERNAL_ERROR("internal_error", HttpStatus.INTERNAL_SERVER_ERROR),

    /** Sign-in failed. Identical for every cause, so it can never enumerate accounts. */
    INVALID_CREDENTIALS("invalid_credentials", HttpStatus.UNAUTHORIZED),

    /**
     * A verification or password-reset link is unknown, expired, or already spent (task 09).
     *
     * <p>{@code 400} rather than {@code 401}: the caller is not attempting to authenticate, they
     * submitted a value that is no longer valid input. One code for all three causes, so the
     * endpoint cannot be asked "did this token ever exist?".
     */
    INVALID_TOKEN("invalid_token", HttpStatus.BAD_REQUEST),

    /** No resource at this path, or none owned by the caller. */
    NOT_FOUND("not_found", HttpStatus.NOT_FOUND),

    /**
     * A mail action exceeded its per-email or per-address window (ADR 009 §6).
     *
     * <p>{@code 429}, which the {@link #ACCOUNT_LOCKED} javadoc reserves for exactly this: a
     * request quota, as opposed to an account that is temporarily unusable. The limit is keyed on
     * the submitted address and the client IP, never on whether an account exists, so it stays
     * uniform for a registered and an unregistered email alike.
     */
    RATE_LIMITED("rate_limited", HttpStatus.TOO_MANY_REQUESTS),

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
