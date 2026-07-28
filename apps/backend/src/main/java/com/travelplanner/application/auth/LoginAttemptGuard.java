package com.travelplanner.application.auth;

import com.travelplanner.config.AuthSecurityProperties;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.exception.AccountLockedException;
import com.travelplanner.domain.port.LoginAttemptPort;
import com.travelplanner.domain.port.LoginAttemptPort.LoginAttemptKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The bounded lockout from ADR 009 §6 — 5 failures in 15 minutes, database-backed.
 *
 * <p><strong>Keyed on the pair (identifier, client address), not on the identifier alone.</strong>
 * That is the whole point of the ADR's "keyed on both": an identifier-only lock is a trivial
 * targeted denial of service, because anyone who knows a username can lock its owner out of their
 * own account with five deliberately wrong passwords. Keying on the pair confines the lock to the
 * address doing the guessing, so the victim signing in from their own machine is unaffected.
 *
 * <p>Attempts against identifiers that were never registered are counted too. Skipping them would
 * make the lockout itself an existence oracle: an attacker could tell registered addresses apart
 * by which ones can be locked.
 *
 * <p>Distributed guessing from many addresses is not what this control stops — that is the
 * per-endpoint rate limiting PLAN §4.0.9 schedules for Phase 1+.
 */
@Service
@RequiresDatabase
public class LoginAttemptGuard {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptGuard.class);

    private final LoginAttemptPort attempts;
    private final AuthSecurityProperties.Lockout properties;

    public LoginAttemptGuard(LoginAttemptPort attempts, AuthSecurityProperties properties) {
        this.attempts = attempts;
        this.properties = properties.getLockout();
    }

    /**
     * Called before the credential is checked, so a locked key never reaches the password
     * comparison at all.
     *
     * @throws AccountLockedException when the window is exhausted
     */
    public void checkNotLocked(String identifier, String clientIp) {
        LoginAttemptKey key = keyFor(identifier, clientIp);
        Duration window = properties.getWindow();
        int failures = attempts.countFailuresSince(key, Instant.now().minus(window));
        if (failures >= properties.getMaxFailures()) {
            log.warn("Lockout active for identifier hash {} from {} — {} failures in {}",
                    identifier.hashCode(), clientIp, failures, window);
            throw new AccountLockedException(window.toSeconds());
        }
    }

    /** Records one failure and sweeps rows that have already left the window. */
    public void recordFailure(String identifier, String clientIp) {
        LoginAttemptKey key = keyFor(identifier, clientIp);
        attempts.recordFailure(key.identifier(), key.clientIp());
        // Bounded, indexed, and cheap; doing it here rather than in a scheduled job keeps the
        // table a sliding window without adding a scheduler this task has no work order for.
        attempts.purgeOlderThan(Instant.now().minus(properties.getWindow()));
    }

    /** Clears the window after a successful sign-in. */
    public void recordSuccess(String identifier, String clientIp) {
        LoginAttemptKey key = keyFor(identifier, clientIp);
        attempts.clearFailures(key.identifier(), key.clientIp());
    }

    /**
     * Lower-cased, matching the case-insensitive unique indexes on {@code "user"} (ADR 009 §4).
     * Without this, {@code Alice} and {@code alice} would each get their own five attempts.
     */
    private static LoginAttemptKey keyFor(String identifier, String clientIp) {
        String normalised = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        return new LoginAttemptKey(normalised, clientIp == null ? "unknown" : clientIp);
    }
}
