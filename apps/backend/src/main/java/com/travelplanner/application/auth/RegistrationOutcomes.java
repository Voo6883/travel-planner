package com.travelplanner.application.auth;

import com.travelplanner.application.account.AccountLifecycleMailer;
import com.travelplanner.application.account.AccountTokenService;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AccountTokenPurpose;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.model.UserIdentity;
import com.travelplanner.domain.port.UserIdentityRepositoryPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * What happens <em>after</em> {@link RegistrationService} decides which of three things a sign-up
 * request was — and the half of ADR 009 §6 that task 08 could not deliver.
 *
 * <h2>The problem this closes</h2>
 *
 * <p>Task 08 made registration return an identical {@code 202 PENDING_VERIFICATION} whether the
 * address was free, already registered, or the username was taken. That closes the enumeration
 * channel, and it left a real defect behind: a user who picked a taken username was told "check your
 * email", and no mail ever came. From the UI, UC-A01 was unusable.
 *
 * <p>ADR 009 §6 anticipates the fix — the response stays uniform, and the <em>mailbox</em> carries
 * the answer, because only its owner can read it. So all three outcomes send a mail to the submitted
 * address, and which mail it is remains invisible to anyone who does not control that address.
 *
 * <p>A collaborator rather than four more fields on {@link RegistrationService}: that class decides
 * what happened, this one carries it out, and neither needs the other's dependencies.
 */
@Service
@RequiresDatabase
public class RegistrationOutcomes {

    private final UserIdentityRepositoryPort identities;
    private final AccountTokenService tokens;
    private final AccountLifecycleMailer mailer;

    public RegistrationOutcomes(UserIdentityRepositoryPort identities, AccountTokenService tokens,
            AccountLifecycleMailer mailer) {
        this.identities = identities;
        this.tokens = tokens;
        this.mailer = mailer;
    }

    /**
     * A new account exists. Links the {@code LOCAL} provider, mints the verification token, and
     * sends both mails PLAN §4.0.10 lists for a local sign-up.
     *
     * <p>Two mails, not one: §4.0.10's table triggers <em>Welcome</em> on "after local
     * {@code POST /auth/register}" and <em>Email verification</em> on "registration". The plan is
     * authority 1, so both are sent rather than folding the welcome into the verification mail;
     * that consolidation is reported as a follow-up rather than decided here.
     */
    public void accountCreated(User account, String username) {
        identities.save(localIdentityFor(account));
        mailer.welcome(account.email(), username);
        mailer.verification(account.email(),
                tokens.issue(account.id(), AccountTokenPurpose.EMAIL_VERIFICATION));
    }

    /**
     * The address is already registered. Tells its owner to sign in or reset — or, for an account
     * with no local password, to use their provider (ADR 009 §4).
     *
     * <p>No token is minted and nothing about the existing account changes. A sign-up attempt must
     * not be a way to trigger state changes on somebody else's account.
     */
    public void emailAlreadyRegistered(User existing) {
        if (existing.isOAuthOnly()) {
            mailer.providerSignIn(existing);
            return;
        }
        mailer.accountAlreadyExists(existing.email());
    }

    /**
     * The address was free but the username was not, so no account was created.
     *
     * <p>This is the mail that turns a dead end into a recoverable step. The address is
     * unregistered, which is exactly why it is safe to tell it something: the recipient is the
     * person who just used the sign-up form, and the message reveals nothing about any account.
     */
    public void usernameAlreadyTaken(String email, String username) {
        mailer.usernameTaken(email, username);
    }

    /**
     * The {@code LOCAL} row exists from the first moment so that "which providers can sign this
     * account in?" (UC-A11) has one answer everywhere, and so task 10's linking rules never meet an
     * account whose local password is invisible to them.
     */
    private static UserIdentity localIdentityFor(User user) {
        return new UserIdentity(UUID.randomUUID(), user.id(), AuthProvider.LOCAL,
                user.id().toString(), user.email(), Instant.now());
    }
}
