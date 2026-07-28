package com.travelplanner.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.travelplanner.application.account.AccountTestFakes.FakeAccountTokens;
import com.travelplanner.application.auth.AuthTestFakes;
import com.travelplanner.application.auth.AuthTestFakes.FakeUsers;
import com.travelplanner.domain.enums.AccountTokenPurpose;
import com.travelplanner.domain.model.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-A14 — soft delete, PII anonymisation, and the revocation that makes it a deletion. */
class AccountDeletionServiceTest {

    private final FakeUsers users = new FakeUsers();
    private final FakeAccountTokens tokens = new FakeAccountTokens();

    private final AccountTokenService tokenService = AccountTestFakes.tokenService(tokens);
    private final AccountDeletionService service = new AccountDeletionService(
            AccountTestFakes.accountStore(users), tokenService);

    @Test
    void keepsTheRowSoEveryTripKeepsAnOwner() {
        User account = givenAccount();

        service.delete(account.id());

        // PLAN §8 makes `user` the owner of every aggregate. A hard delete would either cascade
        // the person's trips away or fail outright for anyone who has ever used the product.
        assertThat(users.byId).containsKey(account.id());
        assertThat(users.byId.get(account.id()).id()).isEqualTo(account.id());
    }

    @Test
    void erasesEveryColumnThatIdentifiesAPerson() {
        User account = givenAccount();

        service.delete(account.id());

        User deleted = users.byId.get(account.id());
        assertThat(deleted.email())
                .doesNotContain("aisyah")
                // RFC 2606 reserves `.invalid`, so the address can never be delivered or claimed.
                .endsWith("@deleted.invalid");
        assertThat(deleted.username()).isNull();
        assertThat(deleted.passwordHash()).isNull();
        assertThat(deleted.emailVerified()).isFalse();
    }

    @Test
    void marksTheAccountClosedAndUnusable() {
        User account = givenAccount();

        service.delete(account.id());

        User deleted = users.byId.get(account.id());
        // `enabled` is what JwtAuthenticationFilter reads on every request; `deleted_at` is the
        // audit fact that an administrator disabling an account does not produce.
        assertThat(deleted.enabled()).isFalse();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.deletedAt()).isNotNull();
    }

    @Test
    void terminatesEverySessionThroughTheRevocationService() {
        User account = givenAccount();

        service.delete(account.id());

        // ADR 009's own motivating example: without the bump, a deleted account's access token
        // keeps authenticating for another thirty minutes and its refresh token for fourteen days.
        assertThat(users.revocations).isOne();
        assertThat(users.byId.get(account.id()).tokenVersion())
                .isEqualTo(account.tokenVersion() + 1);
    }

    @Test
    void closesEveryOutstandingMailedLink() {
        User account = givenAccount();
        tokenService.issue(account.id(), AccountTokenPurpose.PASSWORD_RESET);
        tokenService.issue(account.id(), AccountTokenPurpose.EMAIL_VERIFICATION);

        service.delete(account.id());

        assertThat(tokens.liveFor(account.id(), AccountTokenPurpose.PASSWORD_RESET)).isEmpty();
        assertThat(tokens.liveFor(account.id(), AccountTokenPurpose.EMAIL_VERIFICATION)).isEmpty();
    }

    @Test
    void isIdempotentSoARetriedRequestIsNotAFailure() {
        User account = givenAccount();
        service.delete(account.id());

        // The second anonymisation would violate the "deleted implies no password" invariant, so
        // it must not be attempted rather than merely being harmless.
        assertThatCode(() -> service.delete(account.id())).doesNotThrowAnyException();
        assertThat(users.revocations).isOne();
    }

    @Test
    void doesNothingForACallerWhoseAccountIsAlreadyGone() {
        assertThatCode(() -> service.delete(UUID.randomUUID())).doesNotThrowAnyException();
        assertThat(users.revocations).isZero();
    }

    private User givenAccount() {
        return users.save(AuthTestFakes.user("aisyah@example.com", "aisyah", "hash:pw"));
    }
}
