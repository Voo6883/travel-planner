package com.travelplanner.application.admin;

import com.travelplanner.config.RequiresDatabase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
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
 * <h2>Why the write is somewhere else</h2>
 *
 * <p>This class is only the trigger. The insert lives in {@link DevAdminSeedWriter} because Spring's
 * transaction support is proxy-based: an annotated method called from a sibling method of the same
 * bean is reached through {@code this} and never through the wrapper, so the annotation does nothing.
 * Holding a reference to another bean is what makes the transaction boundary real rather than merely
 * declared. Idempotence and the credential rationale are documented there.
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

    private final DevAdminSeedWriter writer;

    public DevAdminSeeder(DevAdminSeedWriter writer) {
        this.writer = writer;
    }

    /**
     * Runs after the context is refreshed, and therefore after Flyway has migrated — the seeder
     * needs the {@code "user"} table to exist, and an {@code ApplicationRunner} is the documented
     * point at which it does.
     */
    @Override
    public void run(ApplicationArguments arguments) {
        writer.seed();
    }
}
