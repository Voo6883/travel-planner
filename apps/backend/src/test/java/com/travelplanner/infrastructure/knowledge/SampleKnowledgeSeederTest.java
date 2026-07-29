package com.travelplanner.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Profile;

/**
 * The production gate on the sample seeder.
 *
 * <p><strong>This is the test the task turns on.</strong> The seeder writes invented POIs, invented
 * seasonality bands and placeholder prices. If it could run in production, the product would present
 * fabricated travel content as real — the exact failure PLAN §4.1.0 and ADR 010 exist to prevent, and
 * one that fails silently because a fabricated row looks like a sourced one.
 *
 * <p>The gate is the bean graph rather than a runtime check: under {@code prod} the class is never
 * instantiated, so there is no code path to disarm. {@link ApplicationContextRunner} evaluates
 * {@code @Profile} and {@code @ConditionalOnProperty} exactly as the real container does, and needs
 * neither Docker nor a database.
 */
class SampleKnowledgeSeederTest {

    /** Every bean in the package carries the same three gates; drift between them is the risk. */
    private static final List<Class<?>> GATED_BEANS = List.of(
            SampleKnowledgeSeeder.class, SampleKnowledgeWriter.class, SampleEmbeddingWriter.class);

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withBean(SampleKnowledgeWriter.class, () -> mock(SampleKnowledgeWriter.class))
            // @RequiresDatabase is @ConditionalOnProperty("spring.datasource.url"): without it the
            // bean would be absent for a reason unrelated to the profile, and every assertion below
            // would pass vacuously.
            .withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:5432/ignored")
            .withUserConfiguration(SampleKnowledgeSeeder.class);

    // ---------------------------------------------------------------------------------------
    // Production cannot seed sample knowledge
    // ---------------------------------------------------------------------------------------

    @Test
    void isAbsentUnderTheProdProfileEvenWhenExplicitlyEnabled() {
        contexts.withPropertyValues(
                        "spring.profiles.active=prod",
                        SampleKnowledgeSeeder.ENABLED_PROPERTY + "=true")
                .run(context -> assertThat(context).doesNotHaveBean(SampleKnowledgeSeeder.class));
    }

    @Test
    void isAbsentUnderAProfileNobodyListed() {
        // An allow-list, not a deny-list. `staging` and `preview` are environments a real person
        // could be shown sample travel data in; they get no seeder until somebody adds them here.
        contexts.withPropertyValues(
                        "spring.profiles.active=staging",
                        SampleKnowledgeSeeder.ENABLED_PROPERTY + "=true")
                .run(context -> assertThat(context).doesNotHaveBean(SampleKnowledgeSeeder.class));
    }

    // ---------------------------------------------------------------------------------------
    // Development opts in
    // ---------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"dev", "docker", "local"})
    void isAbsentInDevelopmentUntilAskedFor(String profile) {
        contexts.withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> assertThat(context).doesNotHaveBean(SampleKnowledgeSeeder.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "docker", "local"})
    void isPresentInDevelopmentWhenExplicitlyEnabled(String profile) {
        contexts.withPropertyValues(
                        "spring.profiles.active=" + profile,
                        SampleKnowledgeSeeder.ENABLED_PROPERTY + "=true")
                .run(context -> assertThat(context).hasSingleBean(SampleKnowledgeSeeder.class));
    }

    @Test
    void isAbsentWithNoDatasourceSoTheUnitSuiteNeverNeedsOne() {
        new ApplicationContextRunner()
                .withBean(SampleKnowledgeWriter.class, () -> mock(SampleKnowledgeWriter.class))
                .withUserConfiguration(SampleKnowledgeSeeder.class)
                .withPropertyValues(
                        "spring.profiles.active=local",
                        SampleKnowledgeSeeder.ENABLED_PROPERTY + "=true")
                .run(context -> assertThat(context).doesNotHaveBean(SampleKnowledgeSeeder.class));
    }

    // ---------------------------------------------------------------------------------------
    // The gates must not drift apart
    // ---------------------------------------------------------------------------------------

    @Test
    void everyBeanInThePackageCarriesTheSameProfileAllowList() {
        for (Class<?> gated : GATED_BEANS) {
            Profile profile = gated.getAnnotation(Profile.class);
            assertThat(profile)
                    .describedAs("%s must be profile-gated", gated.getSimpleName())
                    .isNotNull();
            assertThat(profile.value()).containsExactlyInAnyOrder("dev", "docker", "local");
        }
    }

    @Test
    void everyBeanInThePackageCarriesTheSameOptInProperty() {
        for (Class<?> gated : GATED_BEANS) {
            ConditionalOnProperty condition = gated.getAnnotation(ConditionalOnProperty.class);
            assertThat(condition)
                    .describedAs("%s must be opt-in", gated.getSimpleName())
                    .isNotNull();
            assertThat(condition.prefix() + "." + condition.name()[0])
                    .isEqualTo(SampleKnowledgeSeeder.ENABLED_PROPERTY);
            assertThat(condition.havingValue()).isEqualTo("true");
            // matchIfMissing stays false: absent means off.
            assertThat(condition.matchIfMissing()).isFalse();
        }
    }
}
