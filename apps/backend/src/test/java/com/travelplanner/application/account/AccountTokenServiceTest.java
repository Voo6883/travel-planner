package com.travelplanner.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.account.AccountTestFakes.FakeAccountTokens;
import com.travelplanner.application.support.TokenDigest;
import com.travelplanner.config.AccountLifecycleProperties;
import com.travelplanner.domain.enums.AccountTokenPurpose;
import com.travelplanner.domain.exception.InvalidTokenException;
import com.travelplanner.domain.model.AccountToken;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The four properties every mailed link depends on: unguessable, hashed at rest, single-use, and
 * expiring (§4.0.10, ADR 004).
 */
class AccountTokenServiceTest {

    private final FakeAccountTokens tokens = new FakeAccountTokens();
    private final AccountLifecycleProperties properties = new AccountLifecycleProperties();
    private final AccountTokenService service = new AccountTokenService(tokens, properties);

    private final UUID user = UUID.randomUUID();

    @Test
    void storesOnlyTheDigestSoALeakedDumpContainsNoWorkingLinks() {
        String raw = service.issue(user, AccountTokenPurpose.PASSWORD_RESET);

        AccountToken stored = tokens.byId.values().iterator().next();
        assertThat(stored.tokenHash())
                .isEqualTo(TokenDigest.sha256Hex(raw))
                .isNotEqualTo(raw)
                .hasSize(AccountToken.HASH_LENGTH);
    }

    @Test
    void issuesADifferentTokenEveryTime() {
        String first = service.issue(user, AccountTokenPurpose.EMAIL_VERIFICATION);
        String second = service.issue(user, AccountTokenPurpose.EMAIL_VERIFICATION);

        assertThat(first).isNotEqualTo(second);
        // 256 bits base64url-encoded, so guessing one is not a strategy.
        assertThat(first).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    void appliesTheLifetimeThePurposeCarries() {
        // ADR 004 locks one hour for a reset; verification is longer because expiry there costs a
        // user access to the whole product until they ask again.
        service.issue(user, AccountTokenPurpose.PASSWORD_RESET);
        Instant resetExpiry = tokens.liveFor(user, AccountTokenPurpose.PASSWORD_RESET)
                .orElseThrow().expiresAt();
        service.issue(user, AccountTokenPurpose.EMAIL_VERIFICATION);
        Instant verifyExpiry = tokens.liveFor(user, AccountTokenPurpose.EMAIL_VERIFICATION)
                .orElseThrow().expiresAt();

        assertThat(resetExpiry).isBefore(verifyExpiry);
        assertThat(Duration.between(Instant.now(), resetExpiry))
                .isCloseTo(Duration.ofHours(1), Duration.ofMinutes(1));
        assertThat(Duration.between(Instant.now(), verifyExpiry))
                .isCloseTo(Duration.ofHours(24), Duration.ofMinutes(1));
    }

    @Test
    void redeemsATokenOnceAndNeverAgain() {
        String raw = service.issue(user, AccountTokenPurpose.PASSWORD_RESET);

        assertThat(service.redeem(raw, AccountTokenPurpose.PASSWORD_RESET)).isEqualTo(user);
        // A second click, a browser prefetch, or a mail scanner following the link.
        assertThatThrownBy(() -> service.redeem(raw, AccountTokenPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refusesATokenPresentedForADifferentPurpose() {
        // A verification token is handed to anyone who types an address into the sign-up form.
        // Redeeming one as a password reset would make "I can receive mail here" equal
        // "I can set this account's password".
        String verification = service.issue(user, AccountTokenPurpose.EMAIL_VERIFICATION);

        assertThatThrownBy(() -> service.redeem(verification, AccountTokenPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class);
        // And it is still usable for what it was issued for — a wrong-purpose attempt must not
        // burn somebody's verification link.
        assertThat(service.redeem(verification, AccountTokenPurpose.EMAIL_VERIFICATION))
                .isEqualTo(user);
    }

    @Test
    void refusesAnExpiredToken() {
        properties.getTokens().setResetTtl(Duration.ofMillis(-1));
        String raw = service.issue(user, AccountTokenPurpose.PASSWORD_RESET);

        assertThatThrownBy(() -> service.redeem(raw, AccountTokenPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refusesUnknownBlankAndNullTokensWithTheSameFailure() {
        // One outcome for every cause: telling "expired" from "never existed" confirms that a
        // token was once issued, which is a fact about somebody else's mailbox.
        assertThatThrownBy(() -> service.redeem("never-issued", AccountTokenPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.redeem("", AccountTokenPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.redeem(null, AccountTokenPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void invalidatesEarlierLinksOfTheSamePurposeWhenAFreshOneIsIssued() {
        String first = service.issue(user, AccountTokenPurpose.PASSWORD_RESET);
        String second = service.issue(user, AccountTokenPurpose.PASSWORD_RESET);

        // Otherwise three "resend" clicks leave three working links in one mailbox, and the oldest
        // — the one with the most time to leak — stays valid for its full hour.
        assertThatThrownBy(() -> service.redeem(first, AccountTokenPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(service.redeem(second, AccountTokenPurpose.PASSWORD_RESET)).isEqualTo(user);
    }

    @Test
    void doesNotInvalidateTokensOfAnotherPurposeOrAnotherAccount() {
        String verification = service.issue(user, AccountTokenPurpose.EMAIL_VERIFICATION);
        UUID somebodyElse = UUID.randomUUID();
        String theirs = service.issue(somebodyElse, AccountTokenPurpose.EMAIL_VERIFICATION);

        service.issue(user, AccountTokenPurpose.PASSWORD_RESET);

        assertThat(service.redeem(verification, AccountTokenPurpose.EMAIL_VERIFICATION))
                .isEqualTo(user);
        assertThat(service.redeem(theirs, AccountTokenPurpose.EMAIL_VERIFICATION))
                .isEqualTo(somebodyElse);
    }

    @Test
    void revokeAllClosesEveryOutstandingLinkForOnePurpose() {
        String raw = service.issue(user, AccountTokenPurpose.EMAIL_VERIFICATION);

        assertThat(service.revokeAll(user, AccountTokenPurpose.EMAIL_VERIFICATION)).isOne();
        assertThatThrownBy(() -> service.redeem(raw, AccountTokenPurpose.EMAIL_VERIFICATION))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void purgeDiscardsOnlyTokensThatCanNoLongerBeRedeemed() {
        properties.getTokens().setResetTtl(Duration.ofMillis(-1));
        service.issue(user, AccountTokenPurpose.PASSWORD_RESET);
        properties.getTokens().setVerificationTtl(Duration.ofHours(24));
        String live = service.issue(user, AccountTokenPurpose.EMAIL_VERIFICATION);

        assertThat(service.purgeExpired()).isOne();
        assertThat(service.redeem(live, AccountTokenPurpose.EMAIL_VERIFICATION)).isEqualTo(user);
    }
}
