package com.travelplanner.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR 010 §3 — "Production: the application fails to start if any {@code Stub*} knowledge adapter is
 * wired."
 *
 * <p>Every case here is one that is otherwise never discovered at all. A stubbed knowledge base
 * does not throw, time out, or return an empty page: it answers confidently with invented guides
 * and prices, and the only trace is a {@code source_ref} nobody reads. The failure surfaces when a
 * traveller acts on a restaurant that does not exist — which is not a moment a test can reach.
 */
class KnowledgeConfigValidatorTest {

    private static final String[] PROD = {"prod"};
    private static final String[] LOCAL = {"local"};
    private static final String[] NO_PROFILE = {};

    private static final List<String> REAL_ADAPTER = List.of("PgVectorKnowledgeAdapter");
    private static final List<String> STUB_ADAPTER = List.of("StubDestinationKnowledgeAdapter");

    @Test
    void acceptsProductionWiredToTheCuratedCorpus() {
        assertThatCode(() -> KnowledgeConfigValidator.validate(PROD, false, REAL_ADAPTER))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesToStartProductionWithAStubKnowledgeAdapter() {
        assertThatThrownBy(() -> KnowledgeConfigValidator.validate(PROD, false, STUB_ADAPTER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StubDestinationKnowledgeAdapter")
                .hasMessageContaining("prod")
                .hasMessageContaining(KnowledgeConfigValidator.SAMPLE_SEED_PROPERTY);
    }

    /**
     * The seed and the adapter are two separate ways to reach the same fabricated corpus. Guarding
     * only the adapter would leave a real adapter reading sample rows — which is worse, because
     * nothing in the wiring then looks like a stub.
     */
    @Test
    void refusesToStartProductionWithTheSampleSeedEnabled() {
        assertThatThrownBy(() -> KnowledgeConfigValidator.validate(PROD, true, REAL_ADAPTER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(KnowledgeConfigValidator.SAMPLE_SEED_PROPERTY)
                .hasMessageContaining("stub:sample");
    }

    /**
     * The message has to name the knob. An operator who reads "refusing to start" and cannot tell
     * which property to change will reach for the fastest way to make the error stop, and the
     * fastest way is to drop the profile.
     */
    @Test
    void theRefusalNamesThePropertyToChange() {
        assertThatThrownBy(() -> KnowledgeConfigValidator.validate(PROD, true, List.of()))
                .hasMessageContaining("travelplanner.knowledge.sample-seed.enabled=false");
    }

    /**
     * ADR 010 §3 permits the stub in development, flagged {@code sample_data: true} behind a
     * persistent banner. C2 and C3 have to be workable before curation completes, so a rule that
     * failed everywhere would simply be deleted.
     */
    @Test
    void permitsTheStubOutsideProduction() {
        assertThatCode(() -> KnowledgeConfigValidator.validate(LOCAL, true, STUB_ADAPTER))
                .doesNotThrowAnyException();
        assertThatCode(() -> KnowledgeConfigValidator.validate(NO_PROFILE, true, STUB_ADAPTER))
                .doesNotThrowAnyException();
    }

    /** Profile matching is case-insensitive, as it is in {@code MailConfigValidator}. */
    @Test
    void matchesTheProductionProfileWhateverItsCasing() {
        assertThatThrownBy(() -> KnowledgeConfigValidator.validate(new String[] {"PROD"}, true, List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Matched on the {@code Stub*} shape rather than a list of known class names, so a stub added
     * by a later task is refused the day it is written.
     */
    @Test
    void catchesAnyStubAdapterNotOnlyTheOneThatExistsToday() {
        assertThatThrownBy(() -> KnowledgeConfigValidator
                .validate(PROD, false, List.of("StubSomethingNobodyHasWrittenYetAdapter")))
                .isInstanceOf(IllegalStateException.class);

        assertThatCode(() -> KnowledgeConfigValidator.validate(PROD, false, List.of("SeededKnowledgeAdapter")))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesEvenWhenAStubSitsBesideARealAdapter() {
        // Two ports wired at once is a misconfiguration in its own right, but the dangerous half is
        // that the stub might be the one that wins primary selection. Neither may be present.
        assertThatThrownBy(() -> KnowledgeConfigValidator.validate(
                PROD, false, List.of("PgVectorKnowledgeAdapter", "StubDestinationKnowledgeAdapter")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StubDestinationKnowledgeAdapter");
    }
}
