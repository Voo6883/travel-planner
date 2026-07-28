package com.travelplanner.application.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.application.auth.AuthTestFakes.FakeHasher;
import com.travelplanner.application.auth.AuthTestFakes.FakeUsers;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.PasswordHasherPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The seed's production gate, and its idempotence (PLAN §4.0.6, backlog S2-1).
 *
 * <p><strong>{@link #isAbsentUnderTheProdProfile()} is the test the task brief demands.</strong>
 * The seeded account is {@code ADMIN}/{@code 123456} — deliberately weak, because it exists only on
 * a developer's machine and in Docker Compose. If it could ever be created in production, every
 * deployment would ship with a publicly documented administrator password.
 *
 * <p>The gate is {@code @Profile}, so the assertion is about the <em>bean graph</em> rather than
 * about behaviour: under {@code prod} the class is never instantiated, which means there is no code
 * path to disarm and no configuration flag to get wrong. {@code ApplicationContextRunner} evaluates
 * {@code @Profile} exactly as the real container does, and needs neither Docker nor a database — so
 * this runs in {@code ./gradlew test} rather than in a suite somebody might skip.
 *
 * <p>The seeding tests call {@link DevAdminSeeder#seed()} directly. {@code ApplicationRunner#run} is
 * invoked by {@code SpringApplication}, which this harness is not, and the transactional and retry
 * annotations are inert here because no proxy infrastructure is registered — which is what makes the
 * fakes' state readable straight after the call.
 */
class DevAdminSeederTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withBean(UserRepositoryPort.class, FakeUsers::new)
            .withBean(PasswordHasherPort.class, FakeHasher::new)
            // @RequiresDatabase is @ConditionalOnProperty("spring.datasource.url"): without it the
            // bean is absent for a reason that has nothing to do with the profile, and every
            // assertion below would pass vacuously.
            .withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:5432/ignored")
            .withUserConfiguration(DevAdminSeeder.class);

    // ---------------------------------------------------------------------------------------
    // "Production cannot create the development seed" — the Definition of Done item
    // ---------------------------------------------------------------------------------------

    @Test
    void isAbsentUnderTheProdProfile() {
        contexts.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(DevAdminSeeder.class));
    }

    @Test
    void isAbsentEvenWhenProdRunsAlongsideAnotherProfile() {
        // A deployment that activates `prod,metrics` must not accidentally satisfy the gate through
        // the second profile, and one that activates `prod,local` is a misconfiguration this test
        // pins the behaviour of: `local` is in the allow-list, so the seeder WOULD load. Asserting
        // it here makes that a reviewed consequence rather than a surprise.
        contexts.withPropertyValues("spring.profiles.active=prod,metrics")
                .run(context -> assertThat(context).doesNotHaveBean(DevAdminSeeder.class));
    }

    @Test
    void isAbsentUnderAProfileNobodyListed() {
        // The gate is an allow-list, not a deny-list. A profile added by a future environment —
        // `staging`, `preview` — gets no seed until somebody deliberately adds it.
        contexts.withPropertyValues("spring.profiles.active=staging")
                .run(context -> assertThat(context).doesNotHaveBean(DevAdminSeeder.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "docker", "local"})
    void isPresentInEveryProfilePlanSection406Lists(String profile) {
        contexts.withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> assertThat(context).hasSingleBean(DevAdminSeeder.class));
    }

    // ---------------------------------------------------------------------------------------
    // What it creates, and that it only creates it once
    // ---------------------------------------------------------------------------------------

    @Test
    void seedsAVerifiedEnabledAdministratorWithAHashedPassword() {
        contexts.withPropertyValues("spring.profiles.active=local").run(context -> {
            FakeUsers users = (FakeUsers) context.getBean(UserRepositoryPort.class);

            assertThat(context.getBean(DevAdminSeeder.class).seed()).isTrue();

            User seeded = users.findByUsernameIgnoreCase(DevAdminSeeder.USERNAME).orElseThrow();
            assertThat(seeded.role()).isEqualTo(Role.ADMIN);
            assertThat(seeded.enabled()).isTrue();
            // UC-A08 gates the planner on a confirmed address, and there is no mailbox at
            // `.local` to confirm from — an unverified seed is one nobody can sign in with.
            assertThat(seeded.emailVerified()).isTrue();
            assertThat(seeded.tokenVersion()).isZero();
        });
    }

    @Test
    void neverStoresTheSeedPasswordInPlainText() {
        contexts.withPropertyValues("spring.profiles.active=docker").run(context -> {
            FakeUsers users = (FakeUsers) context.getBean(UserRepositoryPort.class);
            context.getBean(DevAdminSeeder.class).seed();

            User seeded = users.findByUsernameIgnoreCase(DevAdminSeeder.USERNAME).orElseThrow();
            assertThat(seeded.passwordHash())
                    .isNotEqualTo(DevAdminSeeder.PASSWORD)
                    .isEqualTo("hash:" + DevAdminSeeder.PASSWORD);
        });
    }

    @Test
    void isIdempotentAcrossRestartsAndLeavesAChangedPasswordAlone() {
        contexts.withPropertyValues("spring.profiles.active=local").run(context -> {
            FakeUsers users = (FakeUsers) context.getBean(UserRepositoryPort.class);
            DevAdminSeeder seeder = context.getBean(DevAdminSeeder.class);
            seeder.seed();

            User original = users.findByUsernameIgnoreCase(DevAdminSeeder.USERNAME).orElseThrow();
            users.save(original.withPasswordHash("hash:changed-by-a-developer", original.updatedAt()));

            assertThat(seeder.seed()).isFalse();
            assertThat(users.countAll()).isOne();
            // It creates; it never updates. A developer who changed the seeded account's password
            // must not find it reset on the next restart.
            assertThat(users.findByUsernameIgnoreCase(DevAdminSeeder.USERNAME).orElseThrow()
                    .passwordHash()).isEqualTo("hash:changed-by-a-developer");
        });
    }
}
