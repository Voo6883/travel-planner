package com.travelplanner.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.auth.AuthTestFakes.FakeRefreshTokens;
import com.travelplanner.application.auth.AuthTestFakes.FakeUsers;
import com.travelplanner.config.AuthSecurityProperties;
import com.travelplanner.domain.exception.UnauthorizedException;
import com.travelplanner.domain.model.RefreshToken;
import com.travelplanner.domain.model.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR 009 §3 — rotation on every use, and what happens when a rotated token comes back. */
class RefreshTokenServiceTest {

    private final FakeUsers users = new FakeUsers();
    private final FakeRefreshTokens tokens = new FakeRefreshTokens();
    private final RefreshTokenService refreshTokens = new RefreshTokenService(tokens,
            new SessionRevocationService(users, tokens), new AuthSecurityProperties());

    private final User user = users.save(AuthTestFakes.user("a@example.com", "aisyah", "hash:x"));

    @Test
    void issuesAnOpaqueTokenAndStoresOnlyItsDigest() {
        String raw = refreshTokens.issue(user.id());

        assertThat(raw).isNotBlank();
        RefreshToken stored = tokens.byId.values().iterator().next();
        // A database dump must not hand out live 14-day sessions, so the raw value is never
        // persisted and the stored value cannot be reversed into it.
        assertThat(stored.tokenHash()).isNotEqualTo(raw).hasSize(RefreshToken.HASH_LENGTH);
        assertThat(stored.expiresAt()).isAfter(java.time.Instant.now().plusSeconds(13 * 86_400));
    }

    @Test
    void rotatesThePresentedTokenAndReturnsItsOwner() {
        String raw = refreshTokens.issue(user.id());

        assertThat(refreshTokens.rotate(raw)).isEqualTo(user.id());
        assertThat(tokens.byId.values().iterator().next().isRotated()).isTrue();
    }

    @Test
    void revokesTheWholeFamilyWhenARotatedTokenIsPresentedAgain() {
        String stolen = refreshTokens.issue(user.id());
        refreshTokens.rotate(stolen);
        String successor = refreshTokens.issue(user.id());

        assertThatThrownBy(() -> refreshTokens.rotate(stolen))
                .isInstanceOf(UnauthorizedException.class);

        // Both parties are signed out, because there is no way to tell the thief from the victim:
        // token_version is bumped and every stored token is revoked (ADR 009 §3).
        assertThat(users.revocations).isOne();
        assertThat(users.byId.get(user.id()).tokenVersion()).isOne();
        assertThatThrownBy(() -> refreshTokens.rotate(successor))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsAnUnknownTokenWithoutRevokingAnything() {
        refreshTokens.issue(user.id());

        assertThatThrownBy(() -> refreshTokens.rotate("not-a-token-we-issued"))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(users.revocations).isZero();
    }

    @Test
    void rejectsAMissingTokenTheSameWayAsAnInvalidOne() {
        assertThatThrownBy(() -> refreshTokens.rotate(null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> refreshTokens.rotate("  ")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        RefreshToken expired = tokens.save(RefreshToken.issued(user.id(),
                RefreshTokenService.hash("expired-raw"), java.time.Instant.now().minusSeconds(1)));

        assertThatThrownBy(() -> refreshTokens.rotate("expired-raw"))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(tokens.byId.get(expired.id()).isRotated()).isFalse();
    }

    @Test
    void revokesOneTokenOnLogoutSoTheCookieCopyStopsWorking() {
        String raw = refreshTokens.issue(user.id());

        refreshTokens.revoke(raw);

        assertThat(tokens.byId.values().iterator().next().revokedAt()).isNotNull();
        assertThatThrownBy(() -> refreshTokens.rotate(raw)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logoutIsIdempotentAndSucceedsWithNoTokenAtAll() {
        refreshTokens.revoke(null);
        refreshTokens.revoke("never-issued");

        assertThat(tokens.byId).isEmpty();
    }

    @Test
    void revokingAllSessionsBumpsTheTokenVersionAndKillsEveryStoredToken() {
        String phone = refreshTokens.issue(user.id());
        String laptop = refreshTokens.issue(user.id());

        refreshTokens.revokeAllSessions(user.id(), SessionRevocationReason.LOGOUT_ALL);

        assertThat(users.byId.get(user.id()).tokenVersion()).isOne();
        assertThat(users.byId.get(user.id()).sessionsValidAfter()).isNotNull();
        assertThatThrownBy(() -> refreshTokens.rotate(phone)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> refreshTokens.rotate(laptop)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void revokingSessionsForAnUnknownAccountReportsThatNothingHappened() {
        SessionRevocationService revocation = new SessionRevocationService(users, tokens);

        assertThat(revocation.revokeAllSessions(UUID.randomUUID(),
                SessionRevocationReason.ADMIN_DISABLED)).isFalse();
    }
}
