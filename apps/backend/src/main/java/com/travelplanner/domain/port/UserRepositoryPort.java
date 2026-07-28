package com.travelplanner.domain.port;

import com.travelplanner.domain.model.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link User}. Implemented in {@code infrastructure/persistence/}.
 *
 * <p>Task 07 established the narrow core; task 08 added the login-by-identifier lookups, the two
 * existence checks registration needs, and the revocation bump ADR 009 §1 requires. Lockout
 * counters live in {@link LoginAttemptPort} because they must count attempts against identifiers
 * that were never registered.
 *
 * <p>Lookups are case-insensitive because the unique indexes are ({@code lower(email)},
 * {@code lower(username)}) — ADR 009 §4. A case-sensitive lookup against a case-insensitive index
 * would let a login attempt miss the row it is about to collide with.
 */
public interface UserRepositoryPort {

    /** Inserts or updates, returning the persisted state including generated audit values. */
    User save(User user);

    Optional<User> findById(UUID userId);

    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * The other half of the "email or username" login path (PLAN §4.0.5). A username may not
     * contain {@code '@'} (ADR 009 §4), so the caller can decide which lookup to run from the
     * submitted string alone — no ambiguous "try both" query that could resolve to two accounts.
     */
    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsById(UUID userId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    /**
     * Revokes every live session for this account (ADR 009 §1) in a single atomic statement:
     * {@code token_version = token_version + 1} plus {@code sessions_valid_after}.
     *
     * <p>Read-modify-write through {@link #save(User)} would be wrong here. Two concurrent
     * revocations — an admin disabling an account while its owner logs out of all devices — would
     * each read the same version and write the same increment, and one revocation would silently
     * be lost.
     *
     * @return the number of rows updated: 1, or 0 when no such user exists
     */
    int revokeSessions(UUID userId, Instant sessionsValidAfter);
}
