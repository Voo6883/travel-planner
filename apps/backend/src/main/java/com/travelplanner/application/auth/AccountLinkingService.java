package com.travelplanner.application.auth;

import com.travelplanner.application.account.AccountLifecycleMailer;
import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.exception.AccountDisabledException;
import com.travelplanner.domain.exception.IdentityAlreadyLinkedException;
import com.travelplanner.domain.exception.ProviderEmailNotVerifiedException;
import com.travelplanner.domain.exception.ProviderEmailUnavailableException;
import com.travelplanner.domain.exception.ProviderLinkRequiredException;
import com.travelplanner.domain.exception.UnauthorizedException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.model.UserIdentity;
import com.travelplanner.domain.port.UserIdentityRepositoryPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.domain.valueobject.IdentityClaims;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decides which account a verified external identity belongs to (PLAN §4.0.5 names this class;
 * ADR 009 §4 supplies the rules).
 *
 * <h2>The join key is never the email address</h2>
 *
 * <p>Every lookup starts from {@code (provider, subject)}. A provider subject is immutable for the
 * life of the provider account; an address is reassigned, forwarded, aliased, and re-registered.
 * The address is consulted for exactly one question — "is there an account this identity should
 * join rather than duplicate?" — and the answer to that question is never on its own sufficient.
 *
 * <h2>Auto-linking requires proof on both sides</h2>
 *
 * <p>ADR 009 §4 permits an automatic link only when the <strong>existing</strong> account has
 * {@code email_verified = true} <em>and</em> the incoming provider address is verified. The
 * pre-ADR rule checked only the incoming side, and that is the documented pre-hijack takeover:
 *
 * <ol>
 *   <li>an attacker registers locally as {@code victim@gmail.com}. They do not own the mailbox, so
 *       the verification mail reaches the victim and the attacker's row stays unverified;
 *   <li>the victim later signs in with Google, which asserts the same address, verified;
 *   <li>under email-equality linking the victim's Google identity is attached to the
 *       <em>attacker's</em> row — and the attacker's password now opens the victim's trips.
 * </ol>
 *
 * <p>Requiring the existing side to be verified breaks step 3: the attacker could only have
 * verified an address whose mailbox they control, in which case there is no victim.
 *
 * <p>When the automatic path is refused, nothing is created and nothing is linked. The recovery is
 * {@link #link}, where a live session for the existing account is the proof a matching string is
 * not.
 *
 * <h2>Audit</h2>
 *
 * <p>Every linking decision is logged at {@code WARN} with the user id and the provider, and never
 * with an address (PLAN §4.0.2-J2 keeps PII out of logs). These are the events an incident
 * reconstruction needs: a refused auto-link is what an attempted takeover looks like from here.
 */
@Service
@RequiresDatabase
public class AccountLinkingService {

    private static final Logger log = LoggerFactory.getLogger(AccountLinkingService.class);

    private final UserRepositoryPort users;
    private final UserIdentityRepositoryPort identities;
    private final AccountLifecycleMailer mailer;

    public AccountLinkingService(UserRepositoryPort users, UserIdentityRepositoryPort identities,
            AccountLifecycleMailer mailer) {
        this.users = users;
        this.identities = identities;
        this.mailer = mailer;
    }

    /**
     * Resolves a freshly verified identity to the account that may sign in with it (UC-A02, UC-A03,
     * UC-A05, UC-A06, UC-A09).
     *
     * <p>Transactional, and deliberately free of network calls: the provider was contacted by the
     * adapter before this method was entered, so no HTTP holds a database connection
     * ({@code AGENTS.md}).
     *
     * @throws ProviderLinkRequiredException when an account holds the address but auto-linking is
     *         not permitted — see the class javadoc
     */
    @TransactionalWrite
    public LinkedAccount resolveSignIn(IdentityClaims claims) {
        Optional<UserIdentity> linked =
                identities.findByProviderAndSubject(claims.provider(), claims.subject());
        if (linked.isPresent()) {
            return LinkedAccount.returning(liveAccount(linked.get().userId()));
        }
        Optional<User> holder = accountHolding(claims);
        return holder.isEmpty()
                ? LinkedAccount.created(createAccount(claims))
                : LinkedAccount.linked(autoLink(holder.get(), claims));
    }

    /**
     * Attaches a verified identity to the account the caller is already signed in to — the explicit
     * confirmation ADR 009 §4 requires wherever {@link #resolveSignIn} refuses (UC-A09).
     *
     * <p>The account's own address is not consulted. That is the point: ownership is proved by the
     * session, so linking a provider whose address differs is both allowed and ordinary.
     *
     * <p>Idempotent when the identity is already this account's, so a double-submitted confirmation
     * is a success rather than a confusing conflict.
     */
    @TransactionalWrite
    public void link(UUID callerId, IdentityClaims claims) {
        User caller = liveAccount(callerId);
        Optional<UserIdentity> existing =
                identities.findByProviderAndSubject(claims.provider(), claims.subject());
        if (existing.isPresent()) {
            if (existing.get().userId().equals(caller.id())) {
                return;
            }
            log.warn("provider_link_rejected — identity already owned by another account, "
                    + "user_id={} provider={}", caller.id(), claims.provider());
            throw new IdentityAlreadyLinkedException();
        }
        if (alreadyHasProvider(caller.id(), claims.provider())) {
            throw new IdentityAlreadyLinkedException();
        }
        identities.save(identityFor(caller.id(), claims));
        log.warn("provider_linked — explicit confirmation, user_id={} provider={}",
                caller.id(), claims.provider());
    }

    /** ADR 009 §4 in one condition. Both sides verified, or no automatic link. */
    private User autoLink(User account, IdentityClaims claims) {
        requireUsable(account);
        if (!account.emailVerified() || !claims.emailVerified()) {
            // The pre-hijack refusal. Nothing is written: an unverified account must not acquire a
            // sign-in method just because somebody proved they hold the same address.
            log.warn("provider_link_refused — unverified account or provider address, "
                    + "user_id={} provider={} account_verified={} provider_verified={}",
                    account.id(), claims.provider(), account.emailVerified(), claims.emailVerified());
            throw new ProviderLinkRequiredException(claims.provider());
        }
        identities.save(identityFor(account.id(), claims));
        log.warn("provider_auto_linked — both sides verified, user_id={} provider={}",
                account.id(), claims.provider());
        return account;
    }

    /**
     * First sign-in with a provider that no account holds (UC-A02, UC-A03).
     *
     * <p>The account is created {@code email_verified = true} because the provider has confirmed the
     * address — which is also why an address it has <em>not</em> confirmed cannot create one.
     * {@code username} and {@code password_hash} are both null: the person picked neither, and
     * {@code password_hash IS NULL} is what ADR 009 §4 reads to refuse minting a local password
     * later.
     */
    private User createAccount(IdentityClaims claims) {
        if (claims.email() == null || claims.email().isBlank()) {
            throw new ProviderEmailUnavailableException();
        }
        if (!claims.emailVerified()) {
            throw new ProviderEmailNotVerifiedException();
        }
        Instant now = Instant.now();
        User account = users.save(new User(UUID.randomUUID(), null, claims.email(), null, true,
                Role.USER, true, 0, null, null, now, now));
        identities.save(identityFor(account.id(), claims));
        // PLAN §4.0.10 puts the welcome mail behind `is_new_user`, and no verification mail is sent:
        // the provider already confirmed the address, so asking again would be theatre.
        mailer.welcome(account.email(), null);
        log.warn("provider_account_created — user_id={} provider={}", account.id(), claims.provider());
        return account;
    }

    /** Empty when the provider supplied no address, so a missing email never matches an account. */
    private Optional<User> accountHolding(IdentityClaims claims) {
        String email = claims.email();
        return email == null || email.isBlank()
                ? Optional.empty()
                : users.findByEmailIgnoreCase(email);
    }

    private boolean alreadyHasProvider(UUID userId, AuthProvider provider) {
        return identities.findAllByUserId(userId).stream()
                .anyMatch(identity -> identity.provider() == provider);
    }

    private User liveAccount(UUID userId) {
        User user = users.findById(userId).orElseThrow(UnauthorizedException::new);
        requireUsable(user);
        return user;
    }

    /** A closed account is also {@code enabled = false}, so one check covers both (UC-A14). */
    private static void requireUsable(User user) {
        if (!user.enabled() || user.isDeleted()) {
            throw new AccountDisabledException();
        }
    }

    private static UserIdentity identityFor(UUID userId, IdentityClaims claims) {
        return new UserIdentity(UUID.randomUUID(), userId, claims.provider(), claims.subject(),
                claims.email(), Instant.now());
    }
}
