package com.travelplanner.domain.port;

import com.travelplanner.domain.enums.AccountTokenPurpose;
import com.travelplanner.domain.model.AccountToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link AccountToken} (§4.0.10, V8). Implemented in
 * {@code infrastructure/persistence/}.
 *
 * <p>Lookup is by digest only. There is deliberately no "find the live reset token for this user":
 * such a method would be a way to obtain a redeemable token from a user id, and the whole point of
 * mailing the token is that holding a user id is not enough.
 */
public interface AccountTokenPort {

    AccountToken save(AccountToken token);

    /** The only read path. The raw token is hashed by the caller; this never sees it. */
    Optional<AccountToken> findByTokenHash(String tokenHash);

    /**
     * Marks one token spent, but only if it was not already.
     *
     * <p><strong>This is the single-use guarantee</strong>, and it has to live in the database. A
     * read-then-write in Java loses the race between two simultaneous clicks on the same link —
     * both would read {@code consumed_at IS NULL}, both would write, and a reset token would be
     * redeemable twice. Expressed as a conditional update, the database decides, and it can only
     * decide once.
     *
     * @return 1 when this call was the one that consumed the token, 0 when somebody else already had
     */
    int consume(UUID tokenId, Instant consumedAt);

    /**
     * Invalidates every outstanding token of one purpose for one account, which happens whenever a
     * fresh link is issued.
     *
     * <p>Without it, asking for three reset mails would leave three working links in a mailbox, and
     * the oldest of them — the one most likely to have been forwarded, logged by a mail gateway, or
     * read over somebody's shoulder — would stay valid for its full hour.
     *
     * @return how many tokens were invalidated
     */
    int consumeAllForUser(UUID userId, AccountTokenPurpose purpose, Instant consumedAt);

    /**
     * Discards tokens that can no longer be redeemed, keeping the table bounded. Expired and spent
     * rows have no evidential value — the security event log records that a reset happened.
     */
    int purgeExpiredBefore(Instant cutoff);
}
