package com.travelplanner.application.admin;

import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.PasswordHasherPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The transactional half of the development admin seed (PLAN §4.0.6, backlog S2-1).
 *
 * <h2>Why this is a separate bean</h2>
 *
 * <p>{@link DevAdminSeeder#run} used to call an annotated {@code seed()} on itself. Spring's
 * transaction support is proxy-based: {@code @TransactionalWrite} is applied by a wrapper around the
 * bean, and a call from one of the bean's own methods to another goes straight through {@code this},
 * never touching the wrapper. So the annotation was inert — no transaction, no rollback rules, and
 * no deadlock retry, on the one write that runs while the application is still starting up.
 *
 * <p>It read as correct, which is the problem: the annotation was present, the tests passed (they
 * call the method directly, where no proxy exists either way), and the only way to notice was to
 * know the self-invocation rule. Splitting the write into its own bean makes the boundary a fact
 * about the object graph rather than a fact somebody has to remember — {@code DevAdminSeeder} now
 * holds a reference to the proxy and cannot bypass it.
 *
 * <p>The alternative — {@code @Transactional(propagation = REQUIRES_NEW)} on a self-call, or
 * {@code AopContext.currentProxy()}, or self-injection — all keep the trap in place and add a second
 * one. {@code docs/HANDOFF-REMAINING-WORK.md} records this as a repository-wide pitfall.
 */
@Service
@Profile({"dev", "docker", "local"})
@RequiresDatabase
public class DevAdminSeedWriter {

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeedWriter.class);

    private final UserRepositoryPort users;
    private final PasswordHasherPort hasher;

    public DevAdminSeedWriter(UserRepositoryPort users, PasswordHasherPort hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    /**
     * Creates the development administrator if it is absent.
     *
     * <h2>Idempotence</h2>
     *
     * <p>Two layers, because one is not enough. The existence check skips the ordinary restart; the
     * caught {@link DataIntegrityViolationException} covers two instances starting simultaneously
     * against one database, where both checks pass and {@code ux_user_username_lower} arbitrates. It
     * also never <em>updates</em> an existing row: a developer who changed the seeded account's
     * password would otherwise find it reset on every restart.
     *
     * @return {@code true} when this call created the account, {@code false} when it already existed
     */
    @TransactionalWrite
    public boolean seed() {
        if (users.existsByUsernameIgnoreCase(DevAdminSeeder.USERNAME)) {
            log.debug("Development admin already present — leaving it untouched");
            return false;
        }
        try {
            users.save(newAdmin());
            // No password, and no hash. The fact worth recording is that a privileged account
            // exists in this environment; the credential is in PLAN §4.0.6 for anyone entitled
            // to it, and in a log file for everyone who is not.
            log.warn("Seeded the DEVELOPMENT admin account '{}'. This profile must never be "
                    + "active in production.", DevAdminSeeder.USERNAME);
            return true;
        } catch (DataIntegrityViolationException concurrentSeed) {
            // Two instances starting against one database. The unique index is the real arbiter,
            // and the loser has nothing to do — the account it wanted now exists.
            log.debug("Development admin created concurrently by another instance");
            return false;
        }
    }

    /**
     * {@code emailVerified} is true so the seeded account can actually be used. UC-A08 gates the
     * planner on a confirmed address, and a development admin that cannot get past its own
     * verification banner — with no mailbox at {@code .local} to confirm from — would be a seed
     * nobody can sign in with.
     */
    private User newAdmin() {
        Instant now = Instant.now();
        return new User(UUID.randomUUID(), DevAdminSeeder.USERNAME, DevAdminSeeder.EMAIL,
                hasher.hash(DevAdminSeeder.PASSWORD), true, Role.ADMIN, true, 0, null, null, now,
                now);
    }
}
