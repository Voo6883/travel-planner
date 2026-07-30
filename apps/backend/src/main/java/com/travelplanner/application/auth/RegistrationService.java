package com.travelplanner.application.auth;

import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.MailProperties;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.UserRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Local sign-up (UC-A01).
 *
 * <p><strong>The response is uniform.</strong> ADR 009 §6 requires registration to return an
 * identical result whether or not the account already exists, so this method returns
 * {@code void}: a caller cannot branch on something it is not told. An address that is already
 * registered is simply not registered again, and the user is not told which of the two happened.
 *
 * <p><strong>The answer goes to the mailbox instead.</strong> Task 08 deferred that half; this task
 * delivers it. All three outcomes now send a mail to the submitted address — a verification link, a
 * "you already have an account" notice, or a "that username is taken" notice — through
 * {@link RegistrationOutcomes}. The HTTP response still says nothing, and the person who actually
 * controls the address still finds out what happened. Without it, a username collision was a dead
 * end: the form said "check your email" and nothing ever arrived.
 *
 * <p>A username collision is treated as uniformly as an email collision, for the same reason:
 * reporting "that username is taken" for a username derived from an email address is a weaker but
 * real enumeration channel.
 *
 * <p>Mail is not sent from inside this transaction. {@code MailDispatcher} defers every send to
 * {@code afterCommit}, so a rolled-back or retried registration cannot mail a verification link for
 * an account that does not exist.
 *
 * <p>New accounts start {@code email_verified=false} when a real mail provider is configured, which
 * UC-A08 turns into a login gate. With {@code MAILER_PROVIDER=stub} there is no mailbox to receive
 * the link, so they start verified instead — see {@code autoVerify()}.
 */
@Service
@RequiresDatabase
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepositoryPort users;
    private final PasswordPolicy passwords;
    private final RegistrationOutcomes outcomes;
    private final MailProperties mail;

    public RegistrationService(UserRepositoryPort users, PasswordPolicy passwords,
            RegistrationOutcomes outcomes, MailProperties mail) {
        this.users = users;
        this.passwords = passwords;
        this.outcomes = outcomes;
        this.mail = mail;
    }

    @TransactionalWrite
    public void register(RegisterCommand command) {
        // Runs first, and on every request. A weak password is the caller's own mistake and says
        // nothing about anyone else's account, so rejecting it leaks nothing — and validating
        // after the existence check would make response timing the oracle the check is hiding.
        String passwordHash = passwords.encode(command.password());

        Optional<User> existing = users.findByEmailIgnoreCase(command.email());
        if (existing.isPresent()) {
            log.info("Registration ignored — the address is already registered");
            outcomes.emailAlreadyRegistered(existing.get());
            return;
        }
        if (usernameTaken(command)) {
            log.info("Registration ignored — the username is already taken");
            outcomes.usernameAlreadyTaken(command.email(), command.username());
            return;
        }
        create(command, passwordHash);
    }

    private void create(RegisterCommand command, String passwordHash) {
        try {
            User saved = users.save(newAccount(command, passwordHash, autoVerify()));
            outcomes.accountCreated(saved, command.username());
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // Two simultaneous sign-ups for the same address: the unique index is the real
            // arbiter, and the loser must produce the same silence as the pre-check above rather
            // than a 500 that reveals the collision. No mail either — the winner's verification
            // link is already on its way to the same mailbox.
            log.info("Registration ignored — concurrent duplicate rejected by a unique index");
        }
    }

    private boolean usernameTaken(RegisterCommand command) {
        return command.username() != null && users.existsByUsernameIgnoreCase(command.username());
    }

    /**
     * Whether a new account starts already verified.
     *
     * <p>True only when no real mail provider is configured. With {@code MAILER_PROVIDER=stub}
     * there is no mailbox for a verification link to arrive in, so holding the account at
     * {@code email_verified=false} makes sign-up a dead end: UC-A08 then refuses the login with
     * {@code 403 email_not_verified} and the only way through is to read the link out of the
     * application log at {@code DEBUG}. Requiring that of every developer, and of anyone running
     * this locally, buys no security — the stub proves nothing about who owns the address either
     * way.
     *
     * <p>This cannot reach production. {@code MailConfigValidator} fails startup under the
     * {@code prod} profile when the provider is still the stub, so a deployment that forgot
     * {@code RESEND_API_KEY} stops at boot rather than silently accepting unverified addresses.
     */
    private boolean autoVerify() {
        return mail.isAutoVerifyRegistrations();
    }

    private static User newAccount(RegisterCommand command, String passwordHash, boolean emailVerified) {
        Instant now = Instant.now();
        return new User(UUID.randomUUID(), command.username(), command.email(), passwordHash,
                emailVerified, Role.USER, true, 0, null, null, now, now);
    }
}
