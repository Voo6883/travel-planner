package com.travelplanner.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.account.AccountTestFakes;
import com.travelplanner.application.auth.AuthTestFakes.FakeIdentities;
import com.travelplanner.application.auth.AuthTestFakes.FakeUsers;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.exception.LastSignInMethodException;
import com.travelplanner.domain.exception.ProviderNotLinkedException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.model.UserIdentity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ADR 009 §4's unlink rules: never strand an account, and always terminate its sessions.
 *
 * <p>The revocation assertion is a {@code token_version} bump observed through {@code FakeUsers},
 * which is what {@code SessionRevocationService} performs and what the authentication filter reads.
 * Asserting it here rather than only in the API suite means a future refactor that quietly drops the
 * revocation fails a fast test.
 */
class ProviderUnlinkServiceTest {

    private final FakeUsers users = new FakeUsers();
    private final FakeIdentities identities = new FakeIdentities();

    private final ProviderUnlinkService unlinking = new ProviderUnlinkService(users, identities,
            AccountTestFakes.revocation(users));

    @Test
    void refusesToRemoveTheOnlyWayIntoAnOAuthOnlyAccount() {
        User googleOnly = account(null);
        link(googleOnly, AuthProvider.FIREBASE_GOOGLE);

        assertThatThrownBy(() -> unlinking.unlink(googleOnly.id(), AuthProvider.FIREBASE_GOOGLE))
                .isInstanceOf(LastSignInMethodException.class);

        assertThat(providersOf(googleOnly)).containsExactly(AuthProvider.FIREBASE_GOOGLE);
        assertThat(users.revocations).describedAs("a refusal changes nothing").isZero();
    }

    @Test
    void allowsUnlinkingWhenALocalPasswordRemains() {
        User withPassword = account("hash:password");
        link(withPassword, AuthProvider.LOCAL);
        link(withPassword, AuthProvider.FIREBASE_GOOGLE);

        unlinking.unlink(withPassword.id(), AuthProvider.FIREBASE_GOOGLE);

        assertThat(providersOf(withPassword)).containsExactly(AuthProvider.LOCAL);
    }

    @Test
    void allowsUnlinkingWhenAnotherExternalProviderRemains() {
        User twoProviders = account(null);
        link(twoProviders, AuthProvider.FIREBASE_GOOGLE);
        link(twoProviders, AuthProvider.GITHUB);

        unlinking.unlink(twoProviders.id(), AuthProvider.FIREBASE_GOOGLE);

        assertThat(providersOf(twoProviders)).containsExactly(AuthProvider.GITHUB);
    }

    @Test
    void doesNotCountTheBookkeepingLocalRowAsACredential() {
        // Registration writes a LOCAL identity row, and UC-A14's anonymisation drops the password
        // without dropping that row. Counting the row would let the last real credential go.
        User passwordless = account(null);
        link(passwordless, AuthProvider.LOCAL);
        link(passwordless, AuthProvider.GITHUB);

        assertThatThrownBy(() -> unlinking.unlink(passwordless.id(), AuthProvider.GITHUB))
                .isInstanceOf(LastSignInMethodException.class);
    }

    @Test
    void terminatesEverySessionForTheAccount() {
        User account = account("hash:password");
        link(account, AuthProvider.LOCAL);
        link(account, AuthProvider.GITHUB);

        unlinking.unlink(account.id(), AuthProvider.GITHUB);

        assertThat(users.revocations).isOne();
        assertThat(users.findById(account.id()).orElseThrow().tokenVersion())
                .describedAs("ADR 009 §1 — every issued access token is now stale")
                .isOne();
    }

    @Test
    void refusesToUnlinkAProviderTheAccountNeverLinked() {
        User account = account("hash:password");
        link(account, AuthProvider.LOCAL);

        assertThatThrownBy(() -> unlinking.unlink(account.id(), AuthProvider.GITHUB))
                .isInstanceOf(ProviderNotLinkedException.class);
        assertThat(users.revocations).isZero();
    }

    @Test
    void refusesToUnlinkLocalBecauseThatWouldNotRemoveThePassword() {
        User account = account("hash:password");
        link(account, AuthProvider.LOCAL);
        link(account, AuthProvider.GITHUB);

        assertThatThrownBy(() -> unlinking.unlink(account.id(), AuthProvider.LOCAL))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(providersOf(account)).contains(AuthProvider.LOCAL);
    }

    private User account(String passwordHash) {
        Instant now = Instant.now();
        return users.save(new User(UUID.randomUUID(), null, UUID.randomUUID() + "@example.com",
                passwordHash, true, Role.USER, true, 0, null, null, now, now));
    }

    private void link(User user, AuthProvider provider) {
        identities.save(new UserIdentity(UUID.randomUUID(), user.id(), provider,
                provider + ":" + user.id(), null, Instant.now()));
    }

    private List<AuthProvider> providersOf(User user) {
        return identities.findAllByUserId(user.id()).stream().map(UserIdentity::provider).toList();
    }
}
