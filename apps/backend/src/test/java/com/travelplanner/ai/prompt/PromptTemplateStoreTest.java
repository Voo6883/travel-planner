package com.travelplanner.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** The registry and its strict rendering (PLAN §5.3). */
class PromptTemplateStoreTest {

    private final PromptTemplateStore store = new PromptTemplateStore();

    /** Task 14 must not author product prompts — 19, 21, 25, and 30 own those. */
    @Test
    void shipsWithNoPromptsRegistered() {
        assertThat(new PromptTemplateStore().ids()).isEmpty();
    }

    @Test
    void resolvesTheNewestVersionByDefault() {
        store.register(PromptTemplate.of("greet", 1, "v1"));
        store.register(PromptTemplate.of("greet", 2, "v2"));

        assertThat(store.latest("greet").text()).isEqualTo("v2");
    }

    /** An old {@code ai_call_log} row must still be traceable to the text that produced it. */
    @Test
    void keepsOlderVersionsResolvableByExactVersion() {
        store.register(PromptTemplate.of("greet", 1, "v1"));
        store.register(PromptTemplate.of("greet", 2, "v2"));

        assertThat(store.version("greet", 1).text()).isEqualTo("v1");
    }

    @Test
    void prefersAProviderVariantAndFallsBackToTheNeutralText() {
        store.register(PromptTemplate.of("greet", 1, "neutral"));
        store.register(new PromptTemplate("greet", 1, "anthropic-tuned", "anthropic"));

        assertThat(store.latest("greet", "anthropic").text()).isEqualTo("anthropic-tuned");
        assertThat(store.latest("greet", "openai").text()).isEqualTo("neutral");
    }

    /** Two prompts under one identity means the effective text depends on bean creation order. */
    @Test
    void rejectsADuplicateRegistration() {
        store.register(PromptTemplate.of("greet", 1, "first"));

        assertThatThrownBy(() -> store.register(PromptTemplate.of("greet", 1, "second")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void reportsAnUnknownPromptRatherThanReturningNull() {
        assertThatThrownBy(() -> store.latest("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No prompt registered under 'nope'");
    }

    // ---------------------------------------------------------------------------------------
    // Rendering. Both failures below are invisible at generation time: the model just improvises.
    // ---------------------------------------------------------------------------------------

    @Test
    void substitutesEveryPlaceholder() {
        PromptTemplate template = PromptTemplate.of("t", 1, "Plan {{days}} days in {{city}}.");

        assertThat(template.render(Map.of("days", "5", "city", "Tokyo")))
                .isEqualTo("Plan 5 days in Tokyo.");
    }

    @Test
    void refusesToRenderWithAMissingVariable() {
        PromptTemplate template = PromptTemplate.of("t", 1, "Plan {{days}} days in {{city}}.");

        assertThatThrownBy(() -> template.render(Map.of("days", "5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'city'");
    }

    /** A stale variable means a caller is still computing something the template stopped using. */
    @Test
    void refusesToRenderWithAnUnusedVariable() {
        PromptTemplate template = PromptTemplate.of("t", 1, "Plan {{days}} days.");

        assertThatThrownBy(() -> template.render(Map.of("days", "5", "city", "Tokyo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'city'");
    }

    @Test
    void treatsSubstitutedTextAsLiteralNotAsAReplacementPattern() {
        PromptTemplate template = PromptTemplate.of("t", 1, "Go to {{city}}.");

        assertThat(template.render(Map.of("city", "$1 \\ Tokyo"))).isEqualTo("Go to $1 \\ Tokyo.");
    }

    @Test
    void listsItsVariablesForCallersThatWantToValidateUpFront() {
        assertThat(PromptTemplate.of("t", 1, "{{a}} then {{b}} then {{a}}").variables())
                .containsExactly("a", "b");
    }

    @Test
    void startsVersioningAtOne() {
        assertThatThrownBy(() -> PromptTemplate.of("t", 0, "text"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
