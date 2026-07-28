package com.travelplanner.application.account;

import com.travelplanner.application.support.TokenDigest;
import com.travelplanner.config.AccountLifecycleProperties;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.exception.RateLimitedException;
import com.travelplanner.domain.port.MailRateLimitPort;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ADR 009 §6's rate limit on {@code /auth/password/forgot} and {@code /auth/verify-email/resend}:
 * <strong>per email and per client address</strong>, database-backed.
 *
 * <h2>Why both dimensions</h2>
 *
 * <p>Either alone leaves a hole. A per-email limit does nothing about one machine walking a list of
 * a million addresses — each gets its own untouched quota. A per-IP limit does nothing about a
 * botnet flooding one victim's mailbox from a thousand hosts. The per-IP ceiling is the looser of
 * the two because a university, an office, or a mobile carrier NAT puts many legitimate users
 * behind a single address, and locking them out collectively is its own denial of service.
 *
 * <h2>Why it does not leak account existence</h2>
 *
 * <p>Both counters are keyed on values that exist whether or not an account does — the string the
 * caller typed, and the address they typed it from. The limit is checked <em>before</em> any user
 * lookup, so a registered and an unregistered address consume quota identically and produce
 * identical responses. That is what lets the endpoint have a limit at all without becoming the
 * enumeration oracle ADR 009 §6 closes.
 *
 * <h2>Why only accepted requests are counted</h2>
 *
 * <p>Recording a rejected request too would extend the window every time a hammering client tried
 * again, so a user who clicked "resend" four times in frustration could be locked out for hours
 * rather than the configured window. The limit should bound throughput, not punish retries.
 *
 * <p>The subject is stored as a digest, never as an address — see {@link MailRateLimitPort}.
 */
@Service
@RequiresDatabase
public class MailRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(MailRateLimiter.class);

    private final MailRateLimitPort limits;
    private final AccountLifecycleProperties.MailRateLimit properties;

    public MailRateLimiter(MailRateLimitPort limits, AccountLifecycleProperties properties) {
        this.limits = limits;
        this.properties = properties.getMailRateLimit();
    }

    /**
     * Checks both windows and, if the request is allowed, counts it against both.
     *
     * @throws RateLimitedException when either window is exhausted
     */
    public void checkAndRecord(MailRateLimitScope scope, String email, String clientIp) {
        String emailHash = TokenDigest.sha256Hex(normalise(email));
        String ipHash = TokenDigest.sha256Hex(clientIp == null ? "unknown" : clientIp);
        Instant since = Instant.now().minus(properties.getWindow());

        reject(scope.emailScope(), emailHash, since, properties.getMaxPerEmail());
        reject(scope.ipScope(), ipHash, since, properties.getMaxPerIp());

        limits.record(scope.emailScope(), emailHash);
        limits.record(scope.ipScope(), ipHash);
        // Bounded, indexed, and cheap. Doing it here rather than in a scheduled job keeps the table
        // a sliding window without adding a scheduler this task has no work order for.
        limits.purgeOlderThan(Instant.now().minus(properties.getWindow()));
    }

    private void reject(String scope, String subjectHash, Instant since, int max) {
        if (limits.countSince(scope, subjectHash, since) < max) {
            return;
        }
        // The subject is already a digest, so this line carries no address and no IP.
        log.warn("mail_rate_limited scope={} subject_hash={}", scope,
                subjectHash.substring(0, 12));
        throw new RateLimitedException(properties.getWindow().toSeconds());
    }

    /**
     * Lower-cased and trimmed, matching the case-insensitive unique index on {@code "user"}
     * (ADR 009 §4). Without it, {@code Alice@x.com} and {@code alice@x.com} would each get their own
     * quota for what the rest of the system treats as one address.
     */
    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
