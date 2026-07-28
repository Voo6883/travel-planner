package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * A mail action exceeded its window (ADR 009 §6). Maps to {@code 429 rate_limited}.
 *
 * <p>Carries {@code details.retry_after_seconds} so the UI can show a countdown rather than a
 * button that is guaranteed to fail.
 *
 * <p><strong>Not an existence oracle.</strong> The window is keyed on the submitted address and the
 * client's IP, both of which exist whether or not an account does, so a limited response says only
 * "you have asked a lot", never "this account is real". That is what lets the endpoint keep ADR 009
 * §6's uniform-response promise while still having a limit at all.
 *
 * <p>Distinct from {@link AccountLockedException} despite the identical detail field. That one is
 * {@code 423}: an account is temporarily unusable. This one is {@code 429}: a caller exceeded a
 * request quota, and the account — if there even is one — is unaffected.
 */
public class RateLimitedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "rate_limited";

    public RateLimitedException(long retryAfterSeconds) {
        super(CODE, "Too many requests. Try again later.",
                Map.of("retry_after_seconds", Math.max(0L, retryAfterSeconds)));
    }
}
