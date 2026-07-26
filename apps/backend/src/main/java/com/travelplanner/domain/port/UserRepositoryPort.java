package com.travelplanner.domain.port;

import com.travelplanner.domain.model.User;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link User}. Implemented in {@code infrastructure/persistence/}.
 *
 * <p>Deliberately narrow. Registration, login-by-identifier, lockout counters, and revocation are
 * task 08's and extend this port when they land; predicting their signatures now would mean
 * inventing a contract nobody has reviewed. What is here is what task 07 needs in order to prove
 * that a user-scoped aggregate can be created and read back safely.
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

    boolean existsById(UUID userId);
}
