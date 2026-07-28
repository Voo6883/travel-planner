package com.travelplanner.application.auth;

import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.model.UserIdentity;
import com.travelplanner.domain.port.UserIdentityRepositoryPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import java.time.Instant;
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
 * <p>That is not a dead end for the user — it is a deferral. The mail task (09) sends the
 * submitted address either a verification link or a "you already have an account" notice, which is
 * the only channel that can tell the two apart <em>and</em> only reaches the person who actually
 * controls the address. Until task 09 lands there is no such mail, and this is recorded in the
 * task report rather than papered over with a leaky error code.
 *
 * <p>A username collision is treated the same way, for the same reason: reporting "that username
 * is taken" for a username derived from an email address is a weaker but real enumeration channel.
 *
 * <p>New accounts start {@code email_verified=false}, which UC-A08 turns into a login gate.
 */
@Service
@RequiresDatabase
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepositoryPort users;
    private final UserIdentityRepositoryPort identities;
    private final PasswordPolicy passwords;

    public RegistrationService(UserRepositoryPort users, UserIdentityRepositoryPort identities,
            PasswordPolicy passwords) {
        this.users = users;
        this.identities = identities;
        this.passwords = passwords;
    }

    @TransactionalWrite
    public void register(RegisterCommand command) {
        // Runs first, and on every request. A weak password is the caller's own mistake and says
        // nothing about anyone else's account, so rejecting it leaks nothing — and validating
        // after the existence check would make response timing the oracle the check is hiding.
        String passwordHash = passwords.encode(command.password());

        if (isTaken(command)) {
            log.info("Registration ignored — identifier already in use");
            return;
        }

        try {
            User saved = users.save(newAccount(command, passwordHash));
            identities.save(localIdentityFor(saved));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // Two simultaneous sign-ups for the same address: the unique index is the real
            // arbiter, and the loser must produce the same silence as the pre-check above rather
            // than a 500 that reveals the collision.
            log.info("Registration ignored — concurrent duplicate rejected by a unique index");
        }
    }

    private boolean isTaken(RegisterCommand command) {
        if (users.existsByEmailIgnoreCase(command.email())) {
            return true;
        }
        return command.username() != null && users.existsByUsernameIgnoreCase(command.username());
    }

    private static User newAccount(RegisterCommand command, String passwordHash) {
        Instant now = Instant.now();
        return new User(UUID.randomUUID(), command.username(), command.email(), passwordHash,
                false, Role.USER, true, 0, null, now, now);
    }

    /**
     * The {@code LOCAL} row exists from the first moment so that "which providers can sign this
     * account in?" (UC-A11) has one answer everywhere, and so task 10's linking rules never meet
     * an account whose local password is invisible to them.
     */
    private static UserIdentity localIdentityFor(User user) {
        return new UserIdentity(UUID.randomUUID(), user.id(), AuthProvider.LOCAL,
                user.id().toString(), user.email(), Instant.now());
    }
}
