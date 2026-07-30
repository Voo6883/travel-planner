package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.application.auth.RefreshTokenService;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.exception.UnauthorizedException;
import com.travelplanner.domain.model.RefreshToken;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.RefreshTokenPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ADR 009 §3 under contention: one refresh token, several simultaneous refreshes, one session.
 *
 * <p>This has to be a database test. The rule it checks is not a rule the application layer can
 * state — it is a property of a conditional {@code UPDATE} evaluated under a row lock, and a fake
 * that "locks" with {@code synchronized} proves only that the fake is consistent with itself. What
 * could still be wrong after the unit test passes is the SQL: a predicate that omits
 * {@code rotated_at is null}, an {@code UPDATE} split into a read and a save by a later refactor, or
 * an isolation level that lets both writers see a stale row. Each of those reproduces the original
 * defect and each of them needs PostgreSQL to be visible.
 *
 * <p>Every thread waits on a barrier before presenting the token, so the writers really do overlap.
 * The assertion holds either way, though, which is what makes the test non-flaky: if the threads
 * happen to serialise completely, the later ones are ordinary replays and must fail for that reason
 * instead. There is no scheduling in which two of them may succeed.
 */
class RefreshTokenRotationConcurrencyIT extends AbstractPostgresIntegrationTest {

    private static final int CONCURRENT_REFRESHES = 8;

    @Autowired
    private RefreshTokenService refreshTokens;

    @Autowired
    private RefreshTokenPort tokens;

    @Autowired
    private UserRepositoryPort users;

    @Test
    void onlyOneOfManySimultaneousRefreshesRotatesTheToken() throws Exception {
        UUID userId = persistedUserId();
        String raw = refreshTokens.issue(userId);

        List<Outcome> outcomes = raceToRefresh(raw);

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        // The losers are rejected, not served a second session, and rejected the same way a replay
        // is — a caller holding a spent refresh token learns nothing from the response.
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .hasSize(CONCURRENT_REFRESHES - 1)
                .allSatisfy(outcome ->
                        assertThat(outcome.failure()).isInstanceOf(UnauthorizedException.class));
    }

    @Test
    void theLosersDoNotLeaveASecondUsableTokenBehind() throws Exception {
        UUID userId = persistedUserId();
        String raw = refreshTokens.issue(userId);

        raceToRefresh(raw);

        // The row was claimed exactly once. Before the conditional UPDATE, each loser's
        // read-check-save would have written its own rotation over the winner's and returned the
        // owning user id, so the endpoint would have minted a session per thread.
        RefreshToken stored = tokens.findByTokenHash(sha256Hex(raw)).orElseThrow();
        assertThat(stored.isRotated()).isTrue();
        // Reuse detection fired, so the family is revoked and the access tokens already handed out
        // are dead too (token_version bumped by SessionRevocationService, in its own transaction —
        // the losers' rollback must not be able to undo the revocation).
        assertThat(stored.revokedAt()).isNotNull();
        assertThat(users.findById(userId).orElseThrow().tokenVersion()).isPositive();
    }

    /** What one thread's attempt did — a success carries the owner, a failure carries the throwable. */
    private record Outcome(UUID userId, Throwable failure) {

        boolean succeeded() {
            return failure == null;
        }
    }

    private List<Outcome> raceToRefresh(String raw) throws Exception {
        CyclicBarrier startLine = new CyclicBarrier(CONCURRENT_REFRESHES);
        List<Callable<Outcome>> attempts = new ArrayList<>();
        for (int attempt = 0; attempt < CONCURRENT_REFRESHES; attempt++) {
            attempts.add(() -> attemptRefresh(startLine, raw));
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REFRESHES)) {
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : pool.invokeAll(attempts, 30, TimeUnit.SECONDS)) {
                outcomes.add(future.get());
            }
            return outcomes;
        }
    }

    private Outcome attemptRefresh(CyclicBarrier startLine, String raw) throws Exception {
        startLine.await(20, TimeUnit.SECONDS);
        try {
            return new Outcome(refreshTokens.rotate(raw), null);
        } catch (RuntimeException rejected) {
            return new Outcome(null, rejected);
        }
    }

    /**
     * The digest the service stores. Recomputed here rather than reached for in
     * {@code RefreshTokenService}, whose {@code hash} is package-private — a security-relevant
     * helper should not become part of the API surface because one test in another package wanted
     * it, and duplicating four lines of SHA-256 is the cheaper of the two.
     */
    private static String sha256Hex(String raw) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private UUID persistedUserId() {
        Instant now = Instant.now();
        return users.save(new User(UUID.randomUUID(), null,
                "refresh-race-" + UUID.randomUUID() + "@example.test", null, false, Role.USER, true,
                0, null, null, now, now)).id();
    }
}
