package com.travelplanner.infrastructure.auth.local;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.exception.InvalidCredentialsException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.IdentityProviderPort;
import com.travelplanner.domain.port.PasswordHasherPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.domain.valueobject.IdentityClaims;
import com.travelplanner.domain.valueobject.ProviderCredential;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The {@code LOCAL} identity adapter (PLAN §4.0.5): email <em>or</em> username, plus a password
 * verified against the BCrypt hash this system stores.
 *
 * <p>Which lookup to run is decided from the submitted string: {@code '@'} means email, and a
 * username cannot contain one (ADR 009 §4). That rule is the reason a single {@code login} field
 * is safe at all — without it, a user could register the username {@code victim@example.com} and
 * make the same input resolve to two different accounts.
 *
 * <p><b>Every failure is the same failure.</b> Unknown email, unknown username, wrong password,
 * and an OAuth-only account with no local password all raise {@link InvalidCredentialsException}
 * with no detail. The hash comparison also runs when there is no account, so a missing user and a
 * wrong password take comparable time — an equality check that returns early is a timing oracle
 * for exactly the enumeration the uniform error is there to prevent.
 *
 * <p>What this adapter does <em>not</em> do: create accounts, issue sessions, count failed
 * attempts, or decide whether an unverified address may sign in. It verifies a credential and
 * reports what it found.
 */
@Component
@RequiresDatabase
public class LocalPasswordIdentityAdapter implements IdentityProviderPort {

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwords;

    public LocalPasswordIdentityAdapter(UserRepositoryPort users, PasswordHasherPort passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.LOCAL;
    }

    @Override
    public IdentityClaims authenticate(ProviderCredential credential) {
        String identifier = credential.identifierIfPresent().orElse("");
        Optional<User> candidate = lookup(identifier);

        String storedHash = candidate.map(User::passwordHash).orElse(null);
        boolean verified = passwords.matches(credential.secret(), storedHash);

        if (!verified || candidate.isEmpty()) {
            throw new InvalidCredentialsException();
        }
        User user = candidate.get();
        // The subject is this system's own user id: LOCAL is the provider that owns the account,
        // so there is no external identifier to carry.
        return new IdentityClaims(AuthProvider.LOCAL, user.id().toString(), user.email(),
                user.emailVerified());
    }

    private Optional<User> lookup(String identifier) {
        if (identifier.isBlank()) {
            return Optional.empty();
        }
        return identifier.indexOf('@') >= 0
                ? users.findByEmailIgnoreCase(identifier)
                : users.findByUsernameIgnoreCase(identifier);
    }
}
