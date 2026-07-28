package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class RefreshTokenTest {

    private static final String HASH = "a".repeat(RefreshToken.HASH_LENGTH);

    @Test
    void aFreshlyIssuedTokenIsUsable() {
        RefreshToken token = RefreshToken.issued(UUID.randomUUID(), HASH, future());

        assertThat(token.isUsableAt(Instant.now())).isTrue();
        assertThat(token.isSpent()).isFalse();
        assertThat(token.isRotated()).isFalse();
    }

    @Test
    void rotationSpendsTheTokenWithoutRevokingIt() {
        // The distinction matters: only a *rotated* token coming back means somebody replayed a
        // superseded credential, which is the ADR 009 §3 reuse signal.
        RefreshToken rotated = RefreshToken.issued(UUID.randomUUID(), HASH, future())
                .rotatedAt(Instant.now());

        assertThat(rotated.isRotated()).isTrue();
        assertThat(rotated.isSpent()).isTrue();
        assertThat(rotated.revokedAt()).isNull();
        assertThat(rotated.isUsableAt(Instant.now())).isFalse();
    }

    @Test
    void revocationSpendsTheTokenWithoutLookingLikeAReplay() {
        RefreshToken revoked = RefreshToken.issued(UUID.randomUUID(), HASH, future())
                .revokedAt(Instant.now());

        assertThat(revoked.isRotated()).isFalse();
        assertThat(revoked.isSpent()).isTrue();
        assertThat(revoked.isUsableAt(Instant.now())).isFalse();
    }

    @Test
    void expiryIsExclusiveSoATokenIsDeadAtItsOwnDeadline() {
        Instant deadline = Instant.now();
        RefreshToken token = RefreshToken.issued(UUID.randomUUID(), HASH, deadline);

        assertThat(token.isExpiredAt(deadline)).isTrue();
        assertThat(token.isUsableAt(deadline)).isFalse();
    }

    @Test
    void refusesAHashThatIsNotADigestOfTheDeclaredWidth() {
        // token_hash is char(64). A shorter value would be space-padded by PostgreSQL and would
        // then never match on lookup — a token nobody could ever use, failing silently.
        assertThatThrownBy(() -> RefreshToken.issued(UUID.randomUUID(), "too-short", future()))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> RefreshToken.issued(UUID.randomUUID(), null, future()))
                .isInstanceOf(ValidationFailedException.class);
    }

    private static Instant future() {
        return Instant.now().plusSeconds(3600);
    }
}
