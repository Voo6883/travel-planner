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

    /**
     * The target of an admin action is a closed account (UC-A14, PLAN §4.0.6).
     *
     * <p>{@code 409}: the request is well formed and conflicts with the account's current state. A
     * closed account has been anonymised and holds no credential, so disabling it changes nothing
     * and resetting its password would put a credential back on a row that must never have one —
     * {@code ck_user_deleted_has_no_password} would reject the write anyway, and a typed conflict
     * is a better answer than a constraint violation.
     */
    ACCOUNT_CLOSED("account_closed", HttpStatus.CONFLICT),

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

    /**
     * The Google account behind a Firebase ID token has an unconfirmed address (PLAN §4.0.5).
     *
     * <p>Distinct from {@link #INVALID_FIREBASE_TOKEN} because the token was perfectly valid — the
     * user's next action is with Google, not with us.
     */
    FIREBASE_EMAIL_NOT_VERIFIED("firebase_email_not_verified", HttpStatus.FORBIDDEN),

    /** Authenticated, but not allowed to act on this resource. */
    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN),

    /**
     * The provider identity is already spoken for (PLAN §4.0.5). One external identity may never
     * resolve to two users.
     */
    IDENTITY_ALREADY_LINKED("identity_already_linked", HttpStatus.CONFLICT),

    /** Unhandled server fault. Details never leave the logs. */
    INTERNAL_ERROR("internal_error", HttpStatus.INTERNAL_SERVER_ERROR),

    /** Sign-in failed. Identical for every cause, so it can never enumerate accounts. */
    INVALID_CREDENTIALS("invalid_credentials", HttpStatus.UNAUTHORIZED),

    /**
     * A Firebase ID token did not verify (PLAN §4.0.5). Bad signature, wrong {@code aud}, expired,
     * or a {@code firebase.sign_in_provider} other than {@code google.com} (ADR 009 §4) — one code
     * for all of them, so the endpoint cannot be asked which console setting to attack next.
     */
    INVALID_FIREBASE_TOKEN("invalid_firebase_token", HttpStatus.UNAUTHORIZED),

    /**
     * The OAuth callback presented no {@code state}, or one that does not match the cookie issued
     * at start (ADR 004 Security). {@code 400}: the caller is not attempting to authenticate, they
     * submitted a value that is not valid input.
     */
    INVALID_OAUTH_STATE("invalid_oauth_state", HttpStatus.BAD_REQUEST),

    /**
     * A verification or password-reset link is unknown, expired, or already spent (task 09).
     *
     * <p>{@code 400} rather than {@code 401}: the caller is not attempting to authenticate, they
     * submitted a value that is no longer valid input. One code for all three causes, so the
     * endpoint cannot be asked "did this token ever exist?".
     */
    INVALID_TOKEN("invalid_token", HttpStatus.BAD_REQUEST),

    /**
     * Unlinking would leave the account with no way to sign in (ADR 009 §4). {@code 409}: the
     * request is well formed and conflicts with the account's current state, which is what the
     * caller has to change first.
     */
    LAST_SIGN_IN_METHOD("last_sign_in_method", HttpStatus.CONFLICT),

    /** No resource at this path, or none owned by the caller. */
    NOT_FOUND("not_found", HttpStatus.NOT_FOUND),

    /**
     * The provider supplied no primary, verified, routable address, so no account can be created
     * from it (ADR 009 §4).
     *
     * <p>{@code 422} rather than {@code 400}: the request was well formed and there is nothing in
     * it for the caller to correct — the fix is on the provider's settings page.
     */
    PROVIDER_EMAIL_UNAVAILABLE("provider_email_unavailable", HttpStatus.UNPROCESSABLE_ENTITY),

    /**
     * An account holds this address but auto-linking is not permitted (ADR 009 §4 — the pre-hijack
     * rule). {@code details.provider} names the provider awaiting confirmation.
     */
    PROVIDER_LINK_REQUIRED("provider_link_required", HttpStatus.CONFLICT),

    /**
     * The identity provider could not be reached or answered with a fault. {@code 503}, and
     * deliberately not {@code internal_error}: nothing here failed, nothing was changed, and the
     * caller's correct next action is to retry rather than to report a bug.
     */
    PROVIDER_UNAVAILABLE("provider_unavailable", HttpStatus.SERVICE_UNAVAILABLE),

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

    /**
     * No account with the requested id (PLAN §4.0.6 "standard errors: {@code forbidden},
     * {@code user_not_found}").
     *
     * <p>Distinct from {@link #NOT_FOUND}, which every other surface returns and which deliberately
     * hides whether a resource exists. Only an administrator can reach this code, and an
     * administrator is already entitled to list every account — so telling them "no such user"
     * rather than "no such route" discloses nothing and makes a mistyped id diagnosable.
     */
    USER_NOT_FOUND("user_not_found", HttpStatus.NOT_FOUND),

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
