package com.travelplanner.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.config.AuthSecurityProperties;
import com.travelplanner.domain.exception.AccountLockedException;
import com.travelplanner.domain.port.LoginAttemptPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR 009 §6 — 5 failures / 15 minutes, keyed on the pair (identifier, client address).
 *
 * <p>The keying is the part worth testing hardest. Keying on the identifier alone would make the
 * lockout a weapon: anyone who knows a username could lock its owner out with five wrong guesses.
 */
class LoginAttemptGuardTest {

    private final FakeAttempts attempts = new FakeAttempts();
    private final LoginAttemptGuard guard =
            new LoginAttemptGuard(attempts, new AuthSecurityProperties());

    @Test
    void allowsAttemptsBelowTheThreshold() {
        attempts.failures = 4;

        assertThatCode(() -> guard.checkNotLocked("aisyah", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void locksAtTheFifthFailureWithinTheWindow() {
        attempts.failures = 5;

        assertThatThrownBy(() -> guard.checkNotLocked("aisyah", "10.0.0.1"))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void tellsTheClientHowLongToWaitSoTheUiCanCountDown() {
        attempts.failures = 5;

        AccountLockedException locked = (AccountLockedException) org.assertj.core.api.Assertions
                .catchThrowable(() -> guard.checkNotLocked("aisyah", "10.0.0.1"));

        assertThat(locked.details()).containsEntry("retry_after_seconds", 900L);
    }

    @Test
    void keysOnTheIdentifierAndTheAddressTogether() {
        guard.recordFailure("Aisyah", "10.0.0.1");

        // Both halves reach the port, so a second address cannot inherit the first's counter and
        // a locked address cannot lock the account everywhere.
        assertThat(attempts.recorded).containsExactly("aisyah|10.0.0.1");
    }

    @Test
    void lowerCasesTheIdentifierToMatchTheCaseInsensitiveUniqueIndexes() {
        // Without this, "Aisyah" and "aisyah" would each get their own five attempts against the
        // one account those names both resolve to (ADR 009 §4).
        guard.checkNotLocked("  AISYAH  ", "10.0.0.1");

        assertThat(attempts.lastKey).isEqualTo("aisyah|10.0.0.1");
    }

    @Test
    void countsOnlyFailuresInsideTheWindow() {
        guard.checkNotLocked("aisyah", "10.0.0.1");

        assertThat(attempts.lastSince)
                .isBetween(Instant.now().minusSeconds(905), Instant.now().minusSeconds(895));
    }

    @Test
    void clearsTheWindowOnASuccessfulSignIn() {
        guard.recordSuccess("aisyah", "10.0.0.1");

        assertThat(attempts.cleared).containsExactly("aisyah|10.0.0.1");
    }

    @Test
    void sweepsRowsThatHaveLeftTheWindowWhenRecordingAFailure() {
        // Keeps login_attempt a sliding window rather than an ever-growing log, without adding a
        // scheduler this task has no work order for.
        guard.recordFailure("aisyah", "10.0.0.1");

        assertThat(attempts.purgedBefore).isNotNull();
    }

    @Test
    void survivesAMissingClientAddressRatherThanFailingTheSignIn() {
        guard.recordFailure("aisyah", null);

        assertThat(attempts.recorded).containsExactly("aisyah|unknown");
    }

    private static final class FakeAttempts implements LoginAttemptPort {

        private final List<String> recorded = new ArrayList<>();
        private final List<String> cleared = new ArrayList<>();
        private int failures;
        private String lastKey;
        private Instant lastSince;
        private Instant purgedBefore;

        @Override
        public void recordFailure(String identifier, String clientIp) {
            recorded.add(identifier + "|" + clientIp);
        }

        @Override
        public int countFailuresSince(LoginAttemptKey key, Instant since) {
            lastKey = key.identifier() + "|" + key.clientIp();
            lastSince = since;
            return failures;
        }

        @Override
        public void clearFailures(String identifier, String clientIp) {
            cleared.add(identifier + "|" + clientIp);
        }

        @Override
        public int purgeOlderThan(Instant cutoff) {
            purgedBefore = cutoff;
            return 0;
        }
    }
}
