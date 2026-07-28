package com.travelplanner.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.account.AccountTestFakes.CapturingMailer;
import com.travelplanner.application.account.AccountTestFakes.FakeMailRateLimits;
import com.travelplanner.application.auth.AuthTestFakes;
import com.travelplanner.application.auth.AuthTestFakes.FakeIdentities;
import com.travelplanner.application.auth.AuthTestFakes.FakeUsers;
import com.travelplanner.domain.exception.InvalidCredentialsException;
import com.travelplanner.domain.exception.UnauthorizedException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-A12 — self-service password change, and the revocation ADR 009 §1 requires with it. */
class PasswordChangeServiceTest {

    private static final String CURRENT = "original-password";
    private static final String NEXT = "a-brand-new-password";

    private final FakeUsers users = new FakeUsers();
    private final CapturingMailer mailer = new CapturingMailer();

    private final PasswordChangeService service = new PasswordChangeService(
            AccountTestFakes.accountStore(users),
            AccountTestFakes.lifecycleMailer(mailer, new FakeMailRateLimits(), new FakeIdentities()));

    @Test
    void replacesThePasswordAndTerminatesEverySession() {
        User account = givenAccount();

        service.change(account.id(), CURRENT, NEXT);

        User updated = users.byId.get(account.id());
        assertThat(updated.passwordHash()).isEqualTo("hash:" + NEXT);
        // ADR 009 §1. This is the point of the feature: a user changing their password because
        // they believe they were compromised is telling the system to evict everyone.
        assertThat(updated.tokenVersion()).isEqualTo(account.tokenVersion() + 1);
        assertThat(updated.sessionsValidAfter()).isNotNull();
        assertThat(users.revocations).isOne();
    }

    @Test
    void goesThroughTheRevocationServiceRatherThanTouchingTokenVersionDirectly() {
        // FakeUsers counts calls to revokeSessions, which is the port SessionRevocationService
        // uses. A service that incremented token_version through save() would leave this at zero
        // while still appearing to work — and would silently skip the refresh-token revocation.
        User account = givenAccount();

        service.change(account.id(), CURRENT, NEXT);

        assertThat(users.revocations).isOne();
    }

    @Test
    void sendsAConfirmationSoATakeoverIsVisibleToTheAccountOwner() {
        User account = givenAccount();

        service.change(account.id(), CURRENT, NEXT);

        assertThat(mailer.only().subject()).isEqualTo("Your Travel Planner password was changed");
        assertThat(mailer.only().to()).isEqualTo(account.email());
    }

    @Test
    void refusesAWrongCurrentPasswordAndChangesNothing() {
        User account = givenAccount();

        assertThatThrownBy(() -> service.change(account.id(), "not-the-password", NEXT))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(users.byId.get(account.id()).passwordHash()).isEqualTo("hash:" + CURRENT);
        assertThat(users.revocations).isZero();
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void refusesAnOAuthOnlyAccountWithTheSameCredentialErrorAsAWrongPassword() {
        // password_hash IS NULL can never match, and the response must not reveal that the account
        // has no local password at all.
        User oauthOnly = users.save(AuthTestFakes.user("g@example.com", "gmailuser", null));

        assertThatThrownBy(() -> service.change(oauthOnly.id(), CURRENT, NEXT))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refusesANewPasswordThatRepeatsTheCurrentOne() {
        // A "change" that changes nothing still revokes every session and sends an alarming
        // confirmation mail — a security alert for an event that did not happen.
        User account = givenAccount();

        assertThatThrownBy(() -> service.change(account.id(), CURRENT, CURRENT))
                .isInstanceOf(ValidationFailedException.class);

        assertThat(users.revocations).isZero();
    }

    @Test
    void refusesANewPasswordThatFailsThePolicy() {
        User account = givenAccount();

        assertThatThrownBy(() -> service.change(account.id(), CURRENT, "short"))
                .isInstanceOf(ValidationFailedException.class);

        assertThat(users.byId.get(account.id()).passwordHash()).isEqualTo("hash:" + CURRENT);
        assertThat(users.revocations).isZero();
    }

    @Test
    void refusesACallerWhoseAccountNoLongerExists() {
        assertThatThrownBy(() -> service.change(UUID.randomUUID(), CURRENT, NEXT))
                .isInstanceOf(UnauthorizedException.class);
    }

    private User givenAccount() {
        return users.save(AuthTestFakes.user("aisyah@example.com", "aisyah", "hash:" + CURRENT));
    }
}
