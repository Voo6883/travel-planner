package com.travelplanner.application.admin;

import com.travelplanner.application.account.AccountStore;
import com.travelplanner.application.auth.SessionRevocationReason;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AdminAction;
import com.travelplanner.domain.enums.AdminActionResult;
import com.travelplanner.domain.exception.AccountClosedException;
import com.travelplanner.domain.exception.ForbiddenException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.AdminAuditEvent;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.valueobject.UserContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Account administration (PLAN §4.0.6, UC-A15, UC-A16).
 *
 * <h2>Scope, and what is missing on purpose</h2>
 *
 * <p>Accounts, and nothing else. There is no method here that reads another user's trips, chats,
 * bookings, or LLM content, and none can be added cheaply: {@code TripRepositoryPort} deliberately
 * has no unscoped {@code findById}, so cross-user access would require widening a port and would
 * therefore be visible in review rather than arriving as a one-line convenience.
 *
 * <h2>Both mutations terminate sessions</h2>
 *
 * <p>ADR 009 §1. Disabling an account or resetting its password without bumping
 * {@code token_version} leaves the existing access token working for up to thirty minutes and the
 * refresh token for fourteen days — which is the whole reason ADR 009 exists, and is not acceptable
 * for an account that can spend money. Neither path touches {@code token_version} itself:
 * {@link AdminUserStore#terminateSessions} and {@code AccountStore.replacePassword} both go through
 * {@code SessionRevocationService}, which does the three things revocation actually requires.
 *
 * <h2>Why nothing here is {@code @Transactional}</h2>
 *
 * <p>The same reason {@code AccountStore} is not: revocation runs {@code REQUIRES_NEW}, and a
 * transaction here that had already written the {@code user} row would leave that new transaction
 * blocking on a lock its own caller holds. Each write is a short transaction owned by
 * {@link AdminUserStore}, with the audit row inside it.
 *
 * <h2>Refusals are logged, not audited</h2>
 *
 * <p>A refused action changes nothing, and {@code audit_event} records changes. Recording refusals
 * there would also be futile: the audit write joins the caller's transaction, so a row written on a
 * path that then throws would roll back anyway. Refusals are security-log events instead, which is
 * where an operator looks for "who tried".
 */
@Service
@RequiresDatabase
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final AdminUserDirectory directory;
    private final AdminUserStore store;
    private final AccountStore accounts;

    public AdminUserService(AdminUserDirectory directory, AdminUserStore store,
            AccountStore accounts) {
        this.directory = directory;
        this.store = store;
        this.accounts = accounts;
    }

    /**
     * PLAN §4.0.6 — enable or disable an account. Disabling terminates every session it holds.
     *
     * @throws com.travelplanner.domain.exception.UserNotFoundException when no account has this id
     * @throws AccountClosedException when the owner already deleted the account (UC-A14)
     * @throws ForbiddenException when an administrator tries to disable their own account
     */
    public AdminUserView setEnabled(SetUserEnabledCommand command, UserContext actor) {
        User target = administrableTarget(command.userId(), actor, !command.enabled());
        AdminAuditEvent event = AdminAuditEvent.of(actor.userId(), target.id(),
                command.enabled() ? AdminAction.ENABLE_USER : AdminAction.DISABLE_USER);

        // Before the write, never after: SessionRevocationService runs REQUIRES_NEW, and calling it
        // once the user row is locked by an outer transaction would deadlock. Ordering it first also
        // fails safe — sessions ended for a disable that then failed is an inconvenience; a disable
        // that committed with sessions still live is the hole ADR 009 exists to close.
        AdminActionResult outcome = command.enabled()
                ? AdminActionResult.SUCCESS
                : store.terminateSessions(target.id(), SessionRevocationReason.ADMIN_DISABLED);

        store.applyEnabled(target, command.enabled(), event.withResult(outcome));
        return directory.detail(target.id());
    }

    /**
     * UC-A16 — replace an account's password and terminate every session it holds.
     *
     * <p>The write and the revocation are one call into {@code AccountStore}, which is what makes
     * them inseparable: an endpoint here cannot express the reset without the eviction.
     *
     * @throws com.travelplanner.domain.exception.UserNotFoundException when no account has this id
     * @throws AccountClosedException when the owner already deleted the account (UC-A14)
     * @throws ValidationFailedException when the new password fails the policy, or when the account
     *         signs in only through a provider (ADR 009 §4)
     */
    public void resetPassword(AdminResetPasswordCommand command, UserContext actor) {
        User target = administrableTarget(command.userId(), actor, false);
        requireLocalPassword(target);

        accounts.replacePassword(target, command.newPassword(),
                SessionRevocationReason.ADMIN_PASSWORD_RESET);
        store.recordPasswordReset(
                AdminAuditEvent.of(actor.userId(), target.id(), AdminAction.RESET_PASSWORD));
    }

    /**
     * The target of a mutation, or the typed refusal that stops it.
     *
     * @param lockingOut whether the action would remove the caller's own access, which is only ever
     *        true for a disable. Enabling your own account is harmless and never reaches here
     */
    private User administrableTarget(UUID userId, UserContext actor, boolean lockingOut) {
        User target = directory.require(userId);
        if (target.isDeleted()) {
            // The row survives so trips keep an owner, but V10's CHECK constraint guarantees it
            // holds no credential — there is nothing left to switch off or to give a password to.
            log.warn("admin_action_refused — closed account, actor={} target={}",
                    actor.userId(), userId);
            throw new AccountClosedException();
        }
        if (lockingOut && target.id().equals(actor.userId())) {
            // ADMIN is the only role that can re-enable an account, so an administrator disabling
            // themselves removes the ability to undo it. A lockout with no recovery path is worse
            // than whatever the action was meant to prevent.
            log.warn("admin_action_refused — self-disable, actor={}", actor.userId());
            throw new ForbiddenException("An administrator cannot disable their own account.");
        }
        return target;
    }

    /**
     * ADR 009 §4 — an account with no local password signs in only through Google or GitHub.
     * Minting one for it would add a second, weaker way into an account whose owner deliberately
     * has only one, and doing it on an administrator's say-so rather than the owner's is worse
     * still.
     */
    private static void requireLocalPassword(User target) {
        if (target.isOAuthOnly()) {
            throw ValidationFailedException.field("new_password",
                    "this account signs in through an external provider and has no password");
        }
    }
}
