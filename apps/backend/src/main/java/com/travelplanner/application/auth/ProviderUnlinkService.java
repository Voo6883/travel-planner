package com.travelplanner.application.auth;

import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.exception.LastSignInMethodException;
import com.travelplanner.domain.exception.ProviderNotLinkedException;
import com.travelplanner.domain.exception.UnauthorizedException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.model.UserIdentity;
import com.travelplanner.domain.port.UserIdentityRepositoryPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * {@code DELETE /auth/providers/{provider}} (ADR 009 §4).
 *
 * <h2>Two rules, and both of them are the feature</h2>
 *
 * <p><b>It refuses to strand an account.</b> A Google-only account's Google identity is the whole of
 * its credential set; removing it would not unlink a provider, it would lock the owner out of every
 * trip they have ever planned, permanently and with no way to explain it afterwards. A local
 * password counts as a sign-in method only when one is actually set — {@code password_hash IS NULL}
 * is how ADR 009 §4 identifies an OAuth-only account everywhere else, and counting the bookkeeping
 * {@code LOCAL} identity row instead would let a passwordless account delete its last real way in.
 *
 * <p><b>It terminates every session.</b> Through {@link SessionRevocationService}, with
 * {@link SessionRevocationReason#PROVIDER_UNLINKED}. Unlinking exists for "that provider account is
 * no longer mine", and in that case a session obtained through it has to stop working now rather
 * than at its next expiry. Never by touching {@code token_version} directly ({@code AGENTS.md}):
 * revocation is three things done together, and an endpoint that reimplements it will eventually do
 * two.
 *
 * <h2>Why LOCAL is not unlinkable</h2>
 *
 * <p>The {@code LOCAL} row records that a password exists; deleting it would not delete the
 * password, because {@code LocalPasswordIdentityAdapter} authenticates against {@code user}, not
 * against {@code user_identity}. An endpoint that appears to remove a credential and does not is
 * worse than one that refuses, so it refuses. Removing a password is {@code PUT /auth/password}.
 */
@Service
@RequiresDatabase
public class ProviderUnlinkService {

    private static final Logger log = LoggerFactory.getLogger(ProviderUnlinkService.class);

    private final UserRepositoryPort users;
    private final UserIdentityRepositoryPort identities;
    private final SessionRevocationService revocation;

    public ProviderUnlinkService(UserRepositoryPort users, UserIdentityRepositoryPort identities,
            SessionRevocationService revocation) {
        this.users = users;
        this.identities = identities;
        this.revocation = revocation;
    }

    /**
     * @param userId taken from the session, never from the request. There is no "unlink for user
     *        {id}" on this API at all
     * @throws LastSignInMethodException when this is the account's only usable credential
     * @throws ProviderNotLinkedException when the account never linked the provider
     */
    @TransactionalWrite
    public void unlink(UUID userId, AuthProvider provider) {
        identities.delete(removableIdentity(userId, provider));
        // Called from inside this transaction, and unaffected by it: SessionRevocationService runs
        // REQUIRES_NEW precisely so a revocation is never undone by its caller's rollback. The
        // asymmetry is the safe one — sessions ended for an unlink that then failed is an
        // inconvenience; an unlink that succeeded with sessions still live is the security hole.
        revocation.revokeAllSessions(userId, SessionRevocationReason.PROVIDER_UNLINKED);
        log.warn("provider_unlinked — user_id={} provider={}", userId, provider);
    }

    private UserIdentity removableIdentity(UUID userId, AuthProvider provider) {
        if (provider == AuthProvider.LOCAL) {
            throw ValidationFailedException.field("provider", "LOCAL cannot be unlinked");
        }
        User account = users.findById(userId).orElseThrow(UnauthorizedException::new);
        List<UserIdentity> linked = identities.findAllByUserId(userId);
        UserIdentity target = linked.stream()
                .filter(identity -> identity.provider() == provider)
                .findFirst()
                .orElseThrow(ProviderNotLinkedException::new);

        if (remainingMethods(account, linked, provider) == 0) {
            log.warn("provider_unlink_refused — last sign-in method, user_id={} provider={}",
                    userId, provider);
            throw new LastSignInMethodException();
        }
        return target;
    }

    /**
     * What the account could still sign in with once {@code removed} is gone: a local password if
     * one is set, plus every other external identity. The {@code LOCAL} identity row is
     * deliberately not counted — it is a record that a password once existed, not evidence that one
     * does now.
     */
    private static long remainingMethods(User account, List<UserIdentity> linked,
            AuthProvider removed) {
        long external = linked.stream()
                .filter(identity -> identity.provider() != AuthProvider.LOCAL)
                .filter(identity -> identity.provider() != removed)
                .count();
        return external + (account.isOAuthOnly() ? 0 : 1);
    }
}
