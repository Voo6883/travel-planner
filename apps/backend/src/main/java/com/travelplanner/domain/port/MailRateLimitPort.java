package com.travelplanner.domain.port;

import java.time.Instant;

/**
 * The sliding window behind ADR 009 §6's "rate limit {@code /auth/password/forgot} and
 * {@code /auth/verify-email/resend} per email and per IP" (V9). Implemented in
 * {@code infrastructure/persistence/}.
 *
 * <p><strong>The subject is a digest, never an address.</strong> Callers hash the lower-cased email
 * or the client IP before it reaches this port. PLAN §4.0.10 keeps recipient PII out of logs;
 * storing plaintext addresses in a quota table would reinstate exactly what that rule removes, and
 * a counter only ever needs equality — which a digest preserves perfectly.
 *
 * <p>Separate from {@link LoginAttemptPort} because the two count different things. That one is a
 * lockout over failed credentials, cleared by a successful sign-in; this one counts requests that
 * all succeeded, and has no notion of success to clear it.
 */
public interface MailRateLimitPort {

    /**
     * @param scope for example {@code password_forgot:email} — lower-case, as V9's CHECK requires
     * @param subjectHash SHA-256 hex of the lower-cased email address, or of the client IP
     */
    int countSince(String scope, String subjectHash, Instant since);

    /** Records one request. Never conditional — the count is what decides, not this call. */
    void record(String scope, String subjectHash);

    /** Keeps the table a window rather than an unbounded log of who asked for what. */
    int purgeOlderThan(Instant cutoff);
}
