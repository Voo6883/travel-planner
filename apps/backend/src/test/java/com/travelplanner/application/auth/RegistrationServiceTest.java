package com.travelplanner.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.auth.AuthTestFakes.FakeHasher;
import com.travelplanner.application.auth.AuthTestFakes.FakeIdentities;
import com.travelplanner.application.auth.AuthTestFakes.FakeUsers;
import com.travelplanner.config.AuthSecurityProperties;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.User;
import org.junit.jupiter.api.Test;

/** UC-A01, with ADR 009 §6's uniform-response rule as the property under test. */
class RegistrationServiceTest {

    private final FakeUsers users = new FakeUsers();
    private final FakeIdentities identities = new FakeIdentities();
    private final RegistrationService registration = new RegistrationService(users, identities,
            new PasswordPolicy(new FakeHasher(), new AuthSecurityProperties()));

    @Test
    void createsAnUnverifiedUserAccountWithTheDefaultRole() {
        registration.register(new RegisterCommand("Aisyah@Example.com", "aisyah", "long-enough-pw"));

        User created = users.byId.values().iterator().next();
        assertThat(created.email()).isEqualTo("aisyah@example.com");
        assertThat(created.username()).isEqualTo("aisyah");
        assertThat(created.passwordHash()).isEqualTo("hash:long-enough-pw");
        assertThat(created.role()).isEqualTo(Role.USER);
        assertThat(created.enabled()).isTrue();
        // UC-A01: unverified until the link is clicked, which UC-A08 turns into a login gate.
        assertThat(created.emailVerified()).isFalse();
        assertThat(created.tokenVersion()).isZero();
    }

    @Test
    void storesTheHashRatherThanThePasswordItself() {
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));

        // The fake hasher is deliberately reversible so tests can read it; what is asserted here
        // is that the value stored went through the hasher rather than straight into the column.
        // BcryptPasswordHasherTest covers the one-way property of the real implementation.
        assertThat(users.byId.values()).allSatisfy(user ->
                assertThat(user.passwordHash()).isNotEqualTo("long-enough-pw"));
    }

    @Test
    void linksTheLocalProviderSoAuthMeCanListItFromTheFirstMoment() {
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));

        assertThat(identities.saved).singleElement().satisfies(identity -> {
            assertThat(identity.provider()).isEqualTo(AuthProvider.LOCAL);
            // The subject is this system's own user id: LOCAL owns the account, so there is no
            // external identifier to carry.
            assertThat(identity.providerSubjectId())
                    .isEqualTo(users.byId.keySet().iterator().next().toString());
        });
    }

    @Test
    void isSilentAboutADuplicateEmailRatherThanConfirmingTheAccountExists() {
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));

        // ADR 009 §6: no exception, no second account, no way for the caller to tell.
        assertThatCode(() ->
                registration.register(new RegisterCommand("A@Example.com", "someone", "another-pw")))
                .doesNotThrowAnyException();

        assertThat(users.byId).hasSize(1);
    }

    @Test
    void isEquallySilentAboutADuplicateUsername() {
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));

        assertThatCode(() ->
                registration.register(new RegisterCommand("b@example.com", "AISYAH", "another-pw")))
                .doesNotThrowAnyException();

        assertThat(users.byId).hasSize(1);
    }

    @Test
    void stillRejectsAWeakPasswordBecauseThatRevealsNothingAboutAnyoneElse() {
        assertThatThrownBy(() ->
                registration.register(new RegisterCommand("a@example.com", "aisyah", "short")))
                .isInstanceOf(ValidationFailedException.class);

        assertThat(users.byId).isEmpty();
    }

    @Test
    void validatesThePasswordBeforeCheckingWhetherTheAddressIsTaken() {
        // Otherwise the time to respond differs between a taken and a free address, and timing
        // becomes the oracle the uniform response exists to close.
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));

        assertThatThrownBy(() ->
                registration.register(new RegisterCommand("a@example.com", "other", "short")))
                .isInstanceOf(ValidationFailedException.class);
    }
}
