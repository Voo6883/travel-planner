package com.travelplanner.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Configuration validation (task 14 — "ensuring selected providers have required credentials").
 *
 * <p>Every case here is one that is otherwise discovered in production by a user, not by an
 * operator: a missing key surfaces on the first chat message, a typo in a routing entry never
 * surfaces at all.
 */
class AiConfigValidatorTest {

    /** The everyday case: no profile is production, so the stub is a legitimate choice. */
    private static final String[] DEV = {"dev"};

    private static final String[] PROD = {"prod"};

    @Test
    void acceptsTheShippedDefaultsSoAFreshCheckoutNeedsNoCredentials() {
        assertThatCode(() -> AiConfigValidator.validate(new AiProperties(), DEV)).doesNotThrowAnyException();
    }

    @Test
    void refusesToStartWhenTheDefaultProviderHasNoKey() {
        AiProperties properties = new AiProperties();
        properties.getProvider().setDefaultProvider("anthropic");

        assertThatThrownBy(() -> AiConfigValidator.validate(properties, DEV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    /**
     * A key needed by one feature is still needed at startup. Deferring the check until someone opens
     * the research screen is not meaningfully better than not checking at all.
     */
    @Test
    void refusesToStartWhenARoutedProviderHasNoKeyEvenIfTheDefaultDoes() {
        AiProperties properties = new AiProperties();
        properties.setRouting(Map.of("research", "openai"));

        assertThatThrownBy(() -> AiConfigValidator.validate(properties, DEV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void rejectsAProviderNameThatDoesNotExist() {
        AiProperties properties = new AiProperties();
        properties.setRouting(Map.of("research", "antropic"));

        assertThatThrownBy(() -> AiConfigValidator.validate(properties, DEV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown AI provider 'antropic'");
    }

    @Test
    void acceptsARealProviderOnceItsKeyIsPresent() {
        AiProperties properties = new AiProperties();
        properties.getProvider().setDefaultProvider("anthropic");
        properties.getAnthropic().setApiKey("sk-ant-test");

        assertThatCode(() -> AiConfigValidator.validate(properties, DEV)).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------------------------------
    // The production stub guard. Everything below would otherwise start cleanly and then serve
    // placeholder text to real travellers — an outage nobody is paged for.
    // ---------------------------------------------------------------------------------------

    @Test
    void refusesToStartInProductionOnTheStubChatModel() {
        assertThatThrownBy(() -> AiConfigValidator.validate(new AiProperties(), PROD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stub LLM")
                .hasMessageContaining("prod");
    }

    @Test
    void refusesToStartInProductionWhenOnlyOneFeatureIsRoutedToTheStub() {
        AiProperties properties = new AiProperties();
        properties.getProvider().setDefaultProvider("anthropic");
        properties.getAnthropic().setApiKey("sk-ant-test");
        properties.setRouting(Map.of("research", "stub"));

        assertThatThrownBy(() -> AiConfigValidator.validate(properties, PROD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stub LLM");
    }

    /**
     * The quieter of the two. A stub chat model at least announces itself in its own output; stub
     * embeddings produce deterministic noise, so retrieval returns confidently ranked nonsense and
     * nothing prints the word "stub" anywhere.
     */
    @Test
    void refusesToStartInProductionOnStubEmbeddings() {
        AiProperties properties = new AiProperties();
        properties.getProvider().setDefaultProvider("anthropic");
        properties.getAnthropic().setApiKey("sk-ant-test");

        assertThatThrownBy(() -> AiConfigValidator.validate(properties, PROD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("embedding provider");
    }

    @Test
    void acceptsAFullyConfiguredProductionSetup() {
        AiProperties properties = new AiProperties();
        properties.getProvider().setDefaultProvider("anthropic");
        properties.getAnthropic().setApiKey("sk-ant-test");
        properties.getEmbeddings().setProvider("openai");
        properties.getOpenai().setApiKey("sk-test");

        assertThatCode(() -> AiConfigValidator.validate(properties, PROD)).doesNotThrowAnyException();
    }

    /**
     * Replay is refused for a stronger reason than the stub, not a weaker one.
     *
     * <p>Stub output announces itself — every reply carries {@code [stub]} — so a misconfigured
     * deployment is visibly broken. Replayed output is indistinguishable from a live answer, because it
     * <em>was</em> one: for a different traveller, at some point in the past. A production deployment
     * serving recordings would look entirely healthy while answering everybody with somebody else's
     * conversation.
     */
    @Test
    void refusesToStartInProductionOnTheReplayAdapter() {
        AiProperties properties = new AiProperties();
        properties.getProvider().setDefaultProvider("replay");

        assertThatThrownBy(() -> AiConfigValidator.validate(properties, PROD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("replay adapter")
                .hasMessageContaining("indistinguishable from a live answer");
    }

    @Test
    void acceptsTheReplayAdapterOutsideProduction() {
        AiProperties properties = new AiProperties();
        properties.getProvider().setDefaultProvider("replay");

        assertThatCode(() -> AiConfigValidator.validate(properties, DEV)).doesNotThrowAnyException();
    }

    @Test
    void refusesToRecordProviderResponsesInProduction() {
        // The prompt is only hashed, but a model can echo it back — so recording against real traffic
        // writes traveller content into files somebody may then commit.
        AiProperties properties = new AiProperties();
        properties.getProvider().setDefaultProvider("anthropic");
        properties.getAnthropic().setApiKey("sk-ant-test");
        properties.getEmbeddings().setProvider("openai");
        properties.getOpenai().setApiKey("sk-test");
        properties.getReplay().setRecord(true);

        assertThatThrownBy(() -> AiConfigValidator.validate(properties, PROD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("replay.record=true")
                .hasMessageContaining("echo a traveller's message back");
    }

    @Test
    void namesReplayAmongTheSupportedProvidersWhenOneIsMisspelt() {
        AiProperties properties = new AiProperties();
        properties.setRouting(Map.of("research", "replya"));

        assertThatThrownBy(() -> AiConfigValidator.validate(properties, DEV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown AI provider 'replya'")
                .hasMessageContaining("replay");
    }

    /** The remedy in a missing-key message must not be "turn the model off". */
    @Test
    void doesNotAdvertiseTheStubAsTheProductionDefaultWhenAKeyIsMissing() {
        AiProperties properties = new AiProperties();
        properties.getProvider().setDefaultProvider("anthropic");

        assertThatThrownBy(() -> AiConfigValidator.validate(properties, PROD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY")
                .hasMessageNotContaining("the default, which needs no credentials");
    }

    // ---------------------------------------------------------------------------------------
    // Embedding pinning (ADR 010 §5). These are the cases where nothing would throw at runtime:
    // retrieval simply returns the wrong rows and the agent grounds an answer on them.
    // ---------------------------------------------------------------------------------------

    @Test
    void rejectsAnEmbeddingDimensionThatDisagreesWithTheModel() {
        AiProperties properties = new AiProperties();
        properties.getEmbeddings().setProvider("openai");
        properties.getOpenai().setApiKey("sk-test");
        properties.getEmbeddings().setDimension(3072);

        assertThatThrownBy(() -> AiConfigValidator.validate(properties, DEV))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1536");
    }

    @Test
    void rejectsAnEmbeddingModelOtherThanTheOneAdr010Pins() {
        AiProperties properties = new AiProperties();
        properties.getEmbeddings().setProvider("openai");
        properties.getOpenai().setApiKey("sk-test");
        properties.getEmbeddings().setModel("text-embedding-3-large");
        properties.getEmbeddings().setDimension(3072);

        assertThatThrownBy(() -> AiConfigValidator.validate(properties, DEV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR 010")
                .hasMessageContaining("backfill");
    }

    @Test
    void rejectsAnthropicForEmbeddingsBecauseItServesNoEmbeddingModel() {
        AiProperties properties = new AiProperties();
        properties.getEmbeddings().setProvider("anthropic");
        properties.getAnthropic().setApiKey("sk-ant-test");

        assertThatThrownBy(() -> AiConfigValidator.validate(properties, DEV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void refusesToStartWhenEmbeddingsSelectOpenAiWithNoKey() {
        AiProperties properties = new AiProperties();
        properties.getEmbeddings().setProvider("openai");

        assertThatThrownBy(() -> AiConfigValidator.validate(properties, DEV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void acceptsTheAdr010PinOnceCredentialsArePresent() {
        AiProperties properties = new AiProperties();
        properties.getEmbeddings().setProvider("openai");
        properties.getOpenai().setApiKey("sk-test");

        assertThatCode(() -> AiConfigValidator.validate(properties, DEV)).doesNotThrowAnyException();
    }
}
