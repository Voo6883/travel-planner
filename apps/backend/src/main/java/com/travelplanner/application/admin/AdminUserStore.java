package com.travelplanner.application.admin;

import com.travelplanner.application.auth.SessionRevocationReason;
import com.travelplanner.application.auth.SessionRevocationService;
import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AdminActionResult;
import com.travelplanner.domain.model.AdminAuditEvent;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.AdminAuditPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Every administrative write, <strong>each one paired with the audit row it must not forget</strong>
 * — the same argument {@code AccountStore} makes about revocation, applied to PLAN §4.0.6's "every
 * admin action writes to {@code audit_event}".
 *
 * <h2>Why the pairing lives here</h2>
 *
 * <p>The account change and its audit row share one transaction, so they commit together or not at
 * all. That is what makes the trail trustworthy in both directions: a rolled-back mutation leaves no
 * row claiming it happened, and a mutation that commits cannot commit unaudited. Split across two
 * transactions, either failure mode is silent, and both are the kind that is discovered during an
 * incident rather than before one.
 *
 * <h2>Why revocation is a separate call, and why it comes first</h2>
 *
 * <p>{@link SessionRevocationService#revokeAllSessions} runs {@code REQUIRES_NEW}, on its own
 * connection. Called from inside {@link #applyEnabled} — which has already written the {@code user}
 * row and holds a lock on it — that new transaction would block on a lock its own caller holds, and
 * neither would finish. So {@link #terminateSessions} is its own method, {@link AdminUserService}
 * calls it <em>before</em> the write, and the lock is released by the time the write needs it.
 *
 * <p>The order is chosen for the failure case. Revoking first and then failing to write leaves an
 * account signed out but still enabled: an inconvenience, and its owner simply signs in again.
 * Writing first and failing to revoke leaves an account marked disabled whose existing token keeps
 * working for up to thirty minutes — which is the exact hole ADR 009 exists to close. Only one of
 * those is acceptable, so only one ordering is.
 */
@Service
@RequiresDatabase
public class AdminUserStore {

    private static final Logger log = LoggerFactory.getLogger(AdminUserStore.class);

    private final UserRepositoryPort users;
    private final AdminAuditPort audit;
    private final SessionRevocationService revocation;

    public AdminUserStore(UserRepositoryPort users, AdminAuditPort audit,
            SessionRevocationService revocation) {
        this.users = users;
        this.audit = audit;
        this.revocation = revocation;
    }

    /**
     * ADR 009 §1 — terminate every session for the account, in its own transaction.
     *
     * @return {@link AdminActionResult#SUCCESS} when an account was found and revoked;
     *         {@link AdminActionResult#PARTIAL} when it was not, which means the change is about to
     *         commit against an account that may still hold a live token
     */
    public AdminActionResult terminateSessions(UUID targetId, SessionRevocationReason reason) {
        return revocation.revokeAllSessions(targetId, reason)
                ? AdminActionResult.SUCCESS
                : AdminActionResult.PARTIAL;
    }

    /**
     * PLAN §4.0.6 — switch the account on or off, and record who did it, atomically.
     *
     * @param outcome whatever {@link #terminateSessions} reported, or {@code SUCCESS} for an enable,
     *        which terminates nothing
     */
    @TransactionalWrite
    public User applyEnabled(User target, boolean enabled, AdminAuditEvent outcome) {
        // Re-read rather than reuse the caller's snapshot. terminateSessions ran in its own
        // REQUIRES_NEW transaction and bumped token_version / sessions_valid_after; `target` was
        // loaded before that, and withEnabled copies both fields forward unchanged. Saving it
        // would write the pre-revocation values back and silently undo the eviction — a disable
        // that reports success while the account's token keeps working, which is precisely the
        // hole ADR 009 §1 exists to close. `user` carries no @Version, so nothing else catches it.
        User current = users.findById(target.id()).orElse(target);
        User saved = users.save(current.withEnabled(enabled, Instant.now()));
        audit.record(outcome);
        log.warn("admin_user_{} — actor={} target={} result={}", enabled ? "enabled" : "disabled",
                outcome.actorUserId(), outcome.targetUserId(), outcome.result());
        return saved;
    }

    /**
     * UC-A16 — record a completed password reset.
     *
     * <p>Audit only, because the write and its revocation already happened together inside
     * {@code AccountStore.replacePassword}, which cannot be called from within a transaction for the
     * deadlock reason above. The row is therefore written <em>after</em> the change succeeded, so it
     * can never claim a reset that did not happen; the reverse — a reset that commits and then fails
     * to audit — fails the request loudly, because {@code AdminAuditPort} does not swallow errors.
     */
    @TransactionalWrite
    public void recordPasswordReset(AdminAuditEvent outcome) {
        audit.record(outcome);
        // No password, no hash, and no email address: the audit fact is who did what to whom.
        log.warn("admin_password_reset — actor={} target={}", outcome.actorUserId(),
                outcome.targetUserId());
    }
}
