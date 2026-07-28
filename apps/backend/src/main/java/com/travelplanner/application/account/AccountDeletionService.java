package com.travelplanner.application.account;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AccountTokenPurpose;
import com.travelplanner.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * UC-A14 — a signed-in user closes their own account.
 *
 * <h2>Soft delete, not a row removal</h2>
 *
 * <p>PLAN §8 makes {@code user} the owner of every aggregate, so a hard delete has only two endings
 * and both are wrong: cascade, and the person's trips and itineraries disappear along with any
 * booking record that references them; restrict, and the delete simply fails for anyone who has ever
 * used the product. The row is kept so referential integrity keeps meaning, and everything that
 * identifies a person is erased from it — see {@link User#anonymised}.
 *
 * <h2>Three things have to happen together</h2>
 *
 * <ol>
 *   <li><b>Outstanding links are invalidated.</b> A reset link mailed ten minutes ago would
 *       otherwise still be redeemable. {@code AccountStore} refuses it anyway, because the account
 *       is no longer live — but a link that is dead in two independent ways is the right number.
 *   <li><b>The row is anonymised and stamped.</b> {@code enabled=false} is what the authentication
 *       filter reads; {@code deleted_at} is the audit fact that an administrator disabling an
 *       account (§4.0.6) does not produce.
 *   <li><b>Every session is revoked.</b> Without the {@code token_version} bump, the deleted
 *       account's access token authenticates for another thirty minutes and its refresh token for
 *       another fourteen days — ADR 009's original motivating example.
 * </ol>
 *
 * <p>The last two are one call on {@link AccountStore#anonymise}, so no future endpoint can perform
 * the anonymisation and forget the revocation.
 *
 * <h2>Idempotent</h2>
 *
 * <p>Deleting an already-deleted account succeeds and does nothing. The caller cannot in practice
 * reach this twice — the first call revokes their session — but a retried request must not become a
 * 500, and the second anonymisation would violate the {@code deleted ⇒ no password} check anyway.
 */
@Service
@RequiresDatabase
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final AccountStore accounts;
    private final AccountTokenService tokens;

    public AccountDeletionService(AccountStore accounts, AccountTokenService tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    /**
     * @param userId taken from the session, never from the request body. There is no
     *        "delete user {id}" here at all — administrative deletion is task 12's, with its own
     *        authorisation and its own audit record
     */
    public void delete(UUID userId) {
        Optional<User> account = accounts.liveById(userId);
        if (account.isEmpty()) {
            log.info("account_delete ignored — no live account for the caller");
            return;
        }
        tokens.revokeAll(userId, AccountTokenPurpose.PASSWORD_RESET);
        tokens.revokeAll(userId, AccountTokenPurpose.EMAIL_VERIFICATION);
        accounts.anonymise(account.get());
    }
}
