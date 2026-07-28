package com.travelplanner.application.account;

import com.travelplanner.application.auth.PasswordPolicy;
import com.travelplanner.application.auth.SessionRevocationReason;
import com.travelplanner.application.auth.SessionRevocationService;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.UserRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Every account write the lifecycle flows perform — <strong>each one paired with the session
 * revocation it must not forget</strong>.
 *
 * <h2>Why the pairing lives here</h2>
 *
 * <p>ADR 009 §1 requires a {@code token_version} bump on password change, password reset, and
 * account delete. Three endpoints, three places to forget it, and forgetting is silent: everything
 * appears to work, and the "I was compromised" case fails only for the person it mattered to. So the
 * write and the revocation are one method here, and the endpoints cannot express one without the
 * other.
 *
 * <p>{@code token_version} itself is never touched directly. It goes through
 * {@link SessionRevocationService}, which does three things atomically — bump the version, stamp
 * {@code sessions_valid_after}, and revoke every stored refresh token — and any caller that
 * reimplemented that would eventually implement two of the three.
 *
 * <h2>Why nothing here is {@code @Transactional}</h2>
 *
 * <p>{@link SessionRevocationService#revokeAllSessions} runs {@code REQUIRES_NEW}, on its own
 * connection. A transaction here that had already updated the {@code user} row would leave that new
 * transaction blocking on a lock its own caller holds, and neither would finish. Each write is
 * therefore its own short transaction, and the revocation follows it.
 *
 * <p>The order — write, then revoke — is chosen for the failure case. Revoking first would sign
 * everyone out of an account whose password had <em>not</em> changed. Writing first and failing to
 * revoke leaves a window that closes on its own: the access token expires in thirty minutes, and
 * the endpoint is safely repeatable.
 *
 * <h2>"Live" accounts</h2>
 *
 * <p>Both lookups exclude disabled and soft-deleted rows. Every lifecycle flow wants the same thing
 * — an account that can still be acted on — and a lookup that returned closed accounts would let a
 * deleted address be mailed a fresh verification link.
 */
@Service
@RequiresDatabase
public class AccountStore {

    private static final Logger log = LoggerFactory.getLogger(AccountStore.class);

    private final UserRepositoryPort users;
    private final PasswordPolicy passwords;
    private final SessionRevocationService revocation;

    public AccountStore(UserRepositoryPort users, PasswordPolicy passwords,
            SessionRevocationService revocation) {
        this.users = users;
        this.passwords = passwords;
        this.revocation = revocation;
    }

    /** @return the account, only if it is enabled and not soft-deleted */
    public Optional<User> liveById(UUID userId) {
        return users.findById(userId).filter(AccountStore::isLive);
    }

    /** @return the account, only if it is enabled and not soft-deleted */
    public Optional<User> liveByEmail(String email) {
        return users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .filter(AccountStore::isLive);
    }

    /**
     * Verifies a presented current password. Returns false for an OAuth-only account, which has no
     * stored hash — and takes comparable time doing so, so timing does not reveal which is which.
     */
    public boolean passwordMatches(User account, String rawPassword) {
        return passwords.matches(rawPassword, account.passwordHash());
    }

    /**
     * Runs the policy without writing anything, so a caller can reject an unacceptable password
     * before spending something it cannot get back — a single-use reset token.
     *
     * @throws com.travelplanner.domain.exception.ValidationFailedException if the password is not
     *         acceptable
     */
    public void validatePassword(String rawPassword) {
        passwords.validate(rawPassword);
    }

    /**
     * UC-A07 and UC-A12 — replace the password and terminate every session for the account.
     *
     * <p>The policy runs before anything is written, so an unacceptable password never reaches the
     * database and never triggers a revocation.
     *
     * @param reason recorded in the security event; {@code PASSWORD_CHANGED} for both the
     *        self-service change and the reset, which are the same event from the account's side
     * @throws com.travelplanner.domain.exception.ValidationFailedException if the password is not
     *         acceptable
     */
    public void replacePassword(User account, String rawPassword, SessionRevocationReason reason) {
        String encoded = passwords.encode(rawPassword);
        users.save(account.withPasswordHash(encoded, Instant.now()));
        revocation.revokeAllSessions(account.id(), reason);
    }

    /** UC-A08 — the verification link was followed. No revocation: nothing about a session changed. */
    public void markEmailVerified(User account) {
        users.save(account.withEmailVerified(true, Instant.now()));
        log.info("email_verified user={}", account.id());
    }

    /**
     * UC-A14 — soft delete with PII anonymisation, then revocation.
     *
     * <p>The revocation is what makes it a deletion rather than a rename. Without it, the deleted
     * account's still-valid access token keeps authenticating for up to thirty minutes, and its
     * refresh token for fourteen days — against a row that no longer has an owner.
     */
    public void anonymise(User account) {
        users.save(User.anonymised(account, Instant.now()));
        revocation.revokeAllSessions(account.id(), SessionRevocationReason.ACCOUNT_DELETED);
        // No email address, and no username: this line is the audit fact, not a record of who.
        log.info("account_deleted user={}", account.id());
    }

    private static boolean isLive(User user) {
        return user.enabled() && !user.isDeleted();
    }
}
