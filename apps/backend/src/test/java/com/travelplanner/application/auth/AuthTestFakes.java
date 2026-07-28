package com.travelplanner.application.auth;

import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.model.RefreshToken;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.model.UserIdentity;
import com.travelplanner.domain.port.PasswordHasherPort;
import com.travelplanner.domain.port.RefreshTokenPort;
import com.travelplanner.domain.port.UserIdentityRepositoryPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory ports for the authentication unit tests.
 *
 * <p>Hand-written rather than mocked because these tests are about behaviour across several calls —
 * a token is issued, then rotated, then replayed — and a mock that returns a canned value per call
 * would let a broken implementation pass by never actually persisting anything.
 *
 * <p>They are also what keeps {@code ./gradlew test} free of Docker: every rule these fakes support
 * is a decision the application layer makes, and none of it needs a database to be true.
 */
final class AuthTestFakes {

    private AuthTestFakes() {
    }

    static User user(String email, String username, String passwordHash) {
        Instant now = Instant.now();
        return new User(UUID.randomUUID(), username, email, passwordHash, true, Role.USER, true,
                0, null, now, now);
    }

    static final class FakeUsers implements UserRepositoryPort {

        final Map<UUID, User> byId = new LinkedHashMap<>();
        int revocations;

        @Override
        public User save(User user) {
            byId.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UUID userId) {
            return Optional.ofNullable(byId.get(userId));
        }

        @Override
        public Optional<User> findByEmailIgnoreCase(String email) {
            return byId.values().stream().filter(user -> equalsIgnoringCase(user.email(), email))
                    .findFirst();
        }

        @Override
        public Optional<User> findByUsernameIgnoreCase(String username) {
            return byId.values().stream()
                    .filter(user -> equalsIgnoringCase(user.username(), username)).findFirst();
        }

        @Override
        public boolean existsById(UUID userId) {
            return byId.containsKey(userId);
        }

        @Override
        public boolean existsByEmailIgnoreCase(String email) {
            return findByEmailIgnoreCase(email).isPresent();
        }

        @Override
        public boolean existsByUsernameIgnoreCase(String username) {
            return findByUsernameIgnoreCase(username).isPresent();
        }

        @Override
        public int revokeSessions(UUID userId, Instant sessionsValidAfter) {
            User user = byId.get(userId);
            if (user == null) {
                return 0;
            }
            revocations++;
            byId.put(userId, new User(user.id(), user.username(), user.email(), user.passwordHash(),
                    user.emailVerified(), user.role(), user.enabled(), user.tokenVersion() + 1,
                    sessionsValidAfter, user.createdAt(), sessionsValidAfter));
            return 1;
        }

        private static boolean equalsIgnoringCase(String left, String right) {
            return left != null && right != null
                    && left.toLowerCase(Locale.ROOT).equals(right.toLowerCase(Locale.ROOT));
        }
    }

    static final class FakeIdentities implements UserIdentityRepositoryPort {

        final List<UserIdentity> saved = new ArrayList<>();

        @Override
        public UserIdentity save(UserIdentity identity) {
            saved.add(identity);
            return identity;
        }

        @Override
        public List<UserIdentity> findAllByUserId(UUID userId) {
            return saved.stream().filter(identity -> identity.userId().equals(userId)).toList();
        }
    }

    static final class FakeRefreshTokens implements RefreshTokenPort {

        final Map<UUID, RefreshToken> byId = new LinkedHashMap<>();

        @Override
        public RefreshToken save(RefreshToken token) {
            byId.put(token.id(), token);
            return token;
        }

        @Override
        public Optional<RefreshToken> findByTokenHash(String tokenHash) {
            return byId.values().stream()
                    .filter(token -> token.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public int revokeAllForUser(UUID userId, Instant revokedAt) {
            List<RefreshToken> live = byId.values().stream()
                    .filter(token -> token.userId().equals(userId) && token.revokedAt() == null)
                    .toList();
            live.forEach(token -> byId.put(token.id(), token.revokedAt(revokedAt)));
            return live.size();
        }
    }

    /** Reversible "hashing", so a test can assert which password was stored without a BCrypt round. */
    static final class FakeHasher implements PasswordHasherPort {

        @Override
        public String hash(String rawPassword) {
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String storedHash) {
            return rawPassword != null && ("hash:" + rawPassword).equals(storedHash);
        }
    }
}
