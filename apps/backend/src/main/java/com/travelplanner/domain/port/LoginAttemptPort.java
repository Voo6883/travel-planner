package com.travelplanner.domain.port;

import java.time.Instant;

/**
 * The failed-login window behind the ADR 009 §6 lockout. Implemented in
 * {@code infrastructure/persistence/} over the {@code login_attempt} table (V7).
 *
 * <p>Database-backed, not in-memory: the locked architecture is stateless and multi-instance, and
 * a per-process counter would reset on deploy and count five separate times behind a load
 * balancer.
 *
 * <p>Every method is keyed on the <em>pair</em> {@code (identifier, clientIp)}. Keying on the
 * identifier alone turns the lockout into a targeted denial of service — five wrong passwords from
 * anywhere would lock a known username out of their own account.
 *
 * <p>Identifiers reaching this port are already lower-cased by the caller, matching the
 * case-insensitive unique indexes on {@code "user"} (ADR 009 §4).
 */
public interface LoginAttemptPort {

    void recordFailure(String identifier, String clientIp);

    int countFailuresSince(LoginAttemptKey key, Instant since);

    /** Called on a successful sign-in, so a user who eventually remembers is not still locked. */
    void clearFailures(String identifier, String clientIp);

    /** Discards rows older than {@code cutoff}, keeping the table a window rather than a log. */
    int purgeOlderThan(Instant cutoff);

    /**
     * The composite lockout key, as one value so that {@link #countFailuresSince} stays within the
     * three-parameter limit and no call site can transpose the two strings.
     */
    record LoginAttemptKey(String identifier, String clientIp) {
    }
}
