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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Creates the development administrator on startup (PLAN §4.0.6, backlog S2-1).
 *
 * <h2>Where the profile gate lives, and why it is here rather than in SQL</h2>
 *
 * <p>PLAN §4.0.6 offers two mechanisms — a Flyway migration or a profile-gated seeder — and names
 * the seeder as the alternative "if SQL seed is awkward". It is awkward, for a reason that decides
 * it: Flyway has no notion of a Spring profile, so gating a migration means either a placeholder
 * that a misconfigured deployment silently resolves the wrong way, or a second migration location
 * that {@code flyway.locations} has to select correctly in every environment. Both put the
 * production safety of the seed in a configuration string.
 *
 * <p>{@code @Profile} puts it in the bean graph instead. Under {@code prod} this class is not
 * instantiated at all — there is no code path to disarm, no placeholder to get wrong, and
 * {@code DevAdminSeederTest} asserts the absence rather than trusting it. The migration files stay
 * free of credentials for the same reason, which is also PLAN §4.0.6's "do not store the plaintext
 * seed password in migration output".
 *
 * <h2>Idempotence</h2>
 *
 * <p>Two layers, because one is not enough. The existence check skips the ordinary restart; the
 * caught {@link DataIntegrityViolationException} covers two instances starting simultaneously
 * against one database, where both checks pass and {@code ux_user_username_lower} arbitrates. It
 * also never <em>updates</em> an existing row: a developer who changed the seeded account's password
 * would otherwise find it reset on every restart.
 *
 * <h2>The credentials</h2>
 *
 * <p>{@code ADMIN} / {@code 123456}, exactly as PLAN §4.0.6 locks them, and intentionally weak
 * because they exist only on a developer's machine and in Docker Compose. The password is hashed
 * before it is stored and is never logged, never returned by any endpoint, and never written into a
 * migration file. The one place it appears in plain text is the constant below and the
 * clearly-marked local-development sections of {@code README.md} and {@code AGENTS.md}.
 */
@Component
@Profile({"dev", "docker", "local"})
@RequiresDatabase
public class DevAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeeder.class);

    /** PLAN §4.0.6. Login is case-insensitive, so the stored casing is cosmetic. */
    static final String USERNAME = "ADMIN";

    /**
     * {@code .local} rather than a real domain: RFC 6762 reserves it, so this address can never be
     * registered or delivered to, and a stray mail from a development environment goes nowhere.
     */
    static final String EMAIL = "admin@travelplanner.local";

    /**
     * <strong>Development and Docker only.</strong> PLAN §4.0.6 fixes this value and states plainly
     * that production must never use it — which is what the {@code @Profile} above enforces.
     */
    static final String PASSWORD = "123456";

    private final UserRepositoryPort users;
    private final PasswordHasherPort hasher;

    public DevAdminSeeder(UserRepositoryPort users, PasswordHasherPort hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    /**
     * Runs after the context is refreshed, and therefore after Flyway has migrated — the seeder
     * needs the {@code "user"} table to exist, and an {@code ApplicationRunner} is the documented
     * point at which it does.
     */
    @Override
    public void run(ApplicationArguments arguments) {
        seed();
    }

    /**
     * @return {@code true} when this call created the account, {@code false} when it already existed
     */
    @TransactionalWrite
    public boolean seed() {
        if (users.existsByUsernameIgnoreCase(USERNAME)) {
            log.debug("Development admin already present — leaving it untouched");
            return false;
        }
        try {
            users.save(newAdmin());
            // No password, and no hash. The fact worth recording is that a privileged account
            // exists in this environment; the credential is in PLAN §4.0.6 for anyone entitled
            // to it, and in a log file for everyone who is not.
            log.warn("Seeded the DEVELOPMENT admin account '{}'. This profile must never be "
                    + "active in production.", USERNAME);
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
        return new User(UUID.randomUUID(), USERNAME, EMAIL, hasher.hash(PASSWORD), true,
                Role.ADMIN, true, 0, null, null, now, now);
    }
}
