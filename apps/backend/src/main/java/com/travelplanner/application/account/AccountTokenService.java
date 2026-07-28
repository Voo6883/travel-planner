package com.travelplanner.application.account;

import com.travelplanner.application.support.TokenDigest;
import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.AccountLifecycleProperties;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AccountTokenPurpose;
import com.travelplanner.domain.exception.InvalidTokenException;
import com.travelplanner.domain.model.AccountToken;
import com.travelplanner.domain.port.AccountTokenPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Mints and redeems the single-use tokens behind the mailed links (§4.0.10, ADR 004).
 *
 * <p>The whole security model of email verification and password reset is in this one class, and it
 * rests on four properties:
 *
 * <ol>
 *   <li><b>Unguessable.</b> 256 bits from {@code SecureRandom}, not a signed structure. A signed
 *       token would be forgeable by anyone who obtained the signing key and — more importantly —
 *       cannot be made single-use without server-side state anyway, so the state is the mechanism
 *       and the signature would be decoration.
 *   <li><b>Stored hashed.</b> Only a SHA-256 digest reaches the database, so a leaked dump contains
 *       no working links.
 *   <li><b>Single-use.</b> Redemption is a conditional {@code UPDATE … WHERE consumed_at IS NULL};
 *       the database, not this code, decides which of two simultaneous clicks wins.
 *   <li><b>Expiring.</b> 24 hours for verification, one hour for reset (ADR 004).
 * </ol>
 *
 * <p><strong>The raw token is never logged, stored, or returned twice.</strong> It is created here,
 * handed straight to the mail layer, and forgotten. The only other place it exists is the recipient's
 * mailbox — which is what makes receiving the mail equivalent to proving control of the address.
 */
@Service
@RequiresDatabase
public class AccountTokenService {

    private static final Logger log = LoggerFactory.getLogger(AccountTokenService.class);

    private final AccountTokenPort tokens;
    private final AccountLifecycleProperties.Tokens properties;

    public AccountTokenService(AccountTokenPort tokens, AccountLifecycleProperties properties) {
        this.tokens = tokens;
        this.properties = properties.getTokens();
    }

    /**
     * Issues a fresh token, invalidating any the account already holds for this purpose.
     *
     * <p>The invalidation is not tidiness. Without it, three "resend" clicks leave three working
     * links in a mailbox, and the oldest of them — the one that has had the most time to be
     * forwarded, indexed by a mail gateway, or read over a shoulder — stays valid for its full life.
     *
     * @return the raw token, to be put in a link and nowhere else
     */
    @TransactionalWrite
    public String issue(UUID userId, AccountTokenPurpose purpose) {
        Instant now = Instant.now();
        tokens.consumeAllForUser(userId, purpose, now);

        String rawToken = TokenDigest.randomToken();
        AccountToken.TokenSecret secret = new AccountToken.TokenSecret(
                TokenDigest.sha256Hex(rawToken), now.plus(ttlFor(purpose)));
        tokens.save(AccountToken.issued(userId, purpose, secret));

        // The token itself is absent from this line, on purpose (task 09: "do not log
        // reset/verification tokens"). The user id is what an investigation joins on.
        log.info("account_token_issued purpose={} user={}", purpose, userId);
        return rawToken;
    }

    /**
     * Validates and spends a presented token.
     *
     * @return the account the token was issued for
     * @throws InvalidTokenException for an unknown, expired, already-spent, wrong-purpose, or
     *         simultaneously-redeemed token — uniformly, because distinguishing them would confirm
     *         to the holder of a random string that it was once a real token for a real account
     */
    @TransactionalWrite
    public UUID redeem(String rawToken, AccountTokenPurpose purpose) {
        AccountToken token = find(rawToken).orElseThrow(InvalidTokenException::new);
        if (!token.isRedeemableAt(Instant.now(), purpose)) {
            throw new InvalidTokenException();
        }
        if (tokens.consume(token.id(), Instant.now()) == 0) {
            // Lost the race against a second click. The other one succeeded; this one must not.
            throw new InvalidTokenException();
        }
        log.info("account_token_redeemed purpose={} user={}", purpose, token.userId());
        return token.userId();
    }

    /** Invalidates every outstanding token of one purpose — used when an account is closed. */
    @TransactionalWrite
    public int revokeAll(UUID userId, AccountTokenPurpose purpose) {
        return tokens.consumeAllForUser(userId, purpose, Instant.now());
    }

    /**
     * Discards tokens that can no longer be redeemed. Called opportunistically after an issue rather
     * than from a scheduler, on the same reasoning as {@code LoginAttemptGuard}: it keeps the table
     * bounded without adding a scheduling mechanism this task has no work order for.
     */
    @TransactionalWrite
    public int purgeExpired() {
        return tokens.purgeExpiredBefore(Instant.now());
    }

    private Optional<AccountToken> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(TokenDigest.sha256Hex(rawToken));
    }

    private Duration ttlFor(AccountTokenPurpose purpose) {
        return purpose == AccountTokenPurpose.PASSWORD_RESET
                ? properties.getResetTtl()
                : properties.getVerificationTtl();
    }
}
