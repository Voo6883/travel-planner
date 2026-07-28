package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.AccountTokenPurpose;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One issued verification or password-reset token (§4.0.10, table {@code account_token} from V8).
 *
 * <p><strong>Only the hash lives here.</strong> The raw token exists in the mailed link and in the
 * browser that follows it — never in this record, never in the database, and never in a log line.
 * That is the same rule {@link RefreshToken} follows, for the same reason: a database dump must not
 * be a list of working ways into other people's accounts.
 *
 * <p>Three conditions, all of which must hold for a token to be redeemable:
 *
 * <ul>
 *   <li>{@code consumedAt == null} — single use. A link clicked twice, prefetched by a browser, or
 *       scanned by a mail security appliance works exactly once.
 *   <li>{@code expiresAt} in the future — a link that sat in an abandoned mailbox for a year is not
 *       a standing invitation.
 *   <li>the {@code purpose} matches what the caller is redeeming it for — see
 *       {@link AccountTokenPurpose}.
 * </ul>
 *
 * <p>The first of those is enforced in the database rather than here, by a conditional update on
 * {@code consumed_at}. This record can say whether a token <em>looks</em> spent; only the database
 * can decide which of two simultaneous clicks wins.
 */
public record AccountToken(
        UUID id,
        UUID userId,
        AccountTokenPurpose purpose,
        String tokenHash,
        Instant expiresAt,
        Instant consumedAt,
        Instant createdAt) {

    /** SHA-256 hex digest width, matching {@code account_token.token_hash char(64)}. */
    public static final int HASH_LENGTH = 64;

    public AccountToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        tokenHash = requireHash(tokenHash);
    }

    /** A freshly minted token: unspent, and valid until {@code expiresAt}. */
    public static AccountToken issued(UUID userId, AccountTokenPurpose purpose, TokenSecret secret) {
        return new AccountToken(UUID.randomUUID(), userId, purpose, secret.tokenHash(),
                secret.expiresAt(), null, Instant.now());
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpiredAt(Instant instant) {
        return !expiresAt.isAfter(instant);
    }

    /** Unspent, unexpired, and issued for the purpose being redeemed. */
    public boolean isRedeemableAt(Instant instant, AccountTokenPurpose expected) {
        return !isConsumed() && !isExpiredAt(instant) && purpose == expected;
    }

    public AccountToken consumedAt(Instant instant) {
        return new AccountToken(id, userId, purpose, tokenHash, expiresAt, instant, createdAt);
    }

    /**
     * The two values a caller must compute together — the digest of the token it is about to mail,
     * and when that token stops working.
     *
     * <p>A pair rather than two parameters because {@link #issued} would otherwise take four
     * arguments, which {@code AGENTS.md} caps at three. Grouping them is also the honest shape:
     * neither value is meaningful without the other.
     */
    public record TokenSecret(String tokenHash, Instant expiresAt) {

        public TokenSecret {
            Objects.requireNonNull(expiresAt, "expiresAt");
            tokenHash = requireHash(tokenHash);
        }
    }

    private static String requireHash(String tokenHash) {
        if (tokenHash == null || tokenHash.length() != HASH_LENGTH) {
            throw ValidationFailedException.field("token_hash",
                    "must be a " + HASH_LENGTH + "-character digest");
        }
        return tokenHash;
    }
}
