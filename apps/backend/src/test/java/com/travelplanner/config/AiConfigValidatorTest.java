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

    @Test
    void acceptsTheShippedDefaultsSoAFreshCheckoutNeedsNoCredentials() {
        assertThatCode(() -> AiConfigValidator.validate(new AiProperties())).doesNotThrowAnyException();
    }

    @Test
    void refusesToStartWhenTheDefaultProviderHasNoKey() {
        AiProperties properties = new AiProperties();
        properties.getProvider().setDefaultProvider("anthropic");

        assertThatThrownBy(() -> AiConfigValidator.validate(properties))
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

        assertThatThrownBy(() -> AiConfigValidator.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void rejectsAProviderNameThatDoesNotExist() {
        AiProperties properties = new AiProperties();
        properties.setRouting(Map.of("research", "antropic"));

        assertThatThrownBy(() -> AiConfigValidator.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown AI provider 'antropic'");
    }

    @Test
    void acceptsARealProviderOnceItsKeyIsPresent() {
        AiProperties properties = new AiProperties();
        properties.getProvider().setDefaultProvider("anthropic");
        properties.getAnthropic().setApiKey("sk-ant-test");

        assertThatCode(() -> AiConfigValidator.validate(properties)).doesNotThrowAnyException();
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

        assertThatThrownBy(() -> AiConfigValidator.validate(properties))
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

        assertThatThrownBy(() -> AiConfigValidator.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR 010")
                .hasMessageContaining("backfill");
    }

    @Test
    void rejectsAnthropicForEmbeddingsBecauseItServesNoEmbeddingModel() {
        AiProperties properties = new AiProperties();
        properties.getEmbeddings().setProvider("anthropic");
        properties.getAnthropic().setApiKey("sk-ant-test");

        assertThatThrownBy(() -> AiConfigValidator.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void refusesToStartWhenEmbeddingsSelectOpenAiWithNoKey() {
        AiProperties properties = new AiProperties();
        properties.getEmbeddings().setProvider("openai");

        assertThatThrownBy(() -> AiConfigValidator.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void acceptsTheAdr010PinOnceCredentialsArePresent() {
        AiProperties properties = new AiProperties();
        properties.getEmbeddings().setProvider("openai");
        properties.getOpenai().setApiKey("sk-test");

        assertThatCode(() -> AiConfigValidator.validate(properties)).doesNotThrowAnyException();
    }
}
