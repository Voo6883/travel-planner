package com.travelplanner.ai.stub;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.ai.StopReason;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The default providers — the reason {@code ./gradlew test} needs no API key, no network, and no
 * Docker.
 */
class StubAdapterTest {

    private final StubLlmAdapter llm = new StubLlmAdapter();

    @Test
    void answersTheSamePromptTheSameWayEveryTimeSoAGoldenFileTestIsPossible() {
        Prompt prompt = Prompt.adHoc(List.of(PromptMessage.user("Plan Tokyo")));

        assertThat(llm.complete(prompt, LlmOptions.defaults()))
                .isEqualTo(llm.complete(prompt, LlmOptions.defaults()));
    }

    /** Stub output must never be mistakeable for a model answer about a real destination. */
    @Test
    void marksEveryReplySoItCannotPassForAModelAnswer() {
        String reply = llm.complete(Prompt.adHoc(List.of(PromptMessage.user("Plan Tokyo"))),
                LlmOptions.defaults());

        assertThat(reply).startsWith(StubLlmAdapter.STUB_MARKER).contains("no model configured");
    }

    /**
     * The stub emits the same event shape a live provider does, so a consumer written against it
     * does not discover on its first real call that it never handled a multi-frame stream.
     */
    @Test
    void streamsSeveralTextDeltasThenUsageThenDone() {
        List<LlmEvent> events = llm.stream(
                Prompt.adHoc(List.of(PromptMessage.user("Plan a two week trip"))),
                LlmOptions.defaults()).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).filteredOn(LlmEvent.TextDelta.class::isInstance).hasSizeGreaterThan(1);
        assertThat(events.get(events.size() - 2)).isInstanceOf(LlmEvent.Usage.class);
        assertThat(events.get(events.size() - 1))
                .isEqualTo(new LlmEvent.Done(StopReason.END_TURN));
    }

    @Test
    void reportsNonZeroTokenUsageSoAiCallLogHasSomethingToExercise() {
        LlmEvent.Usage usage = (LlmEvent.Usage) llm.stream(
                Prompt.adHoc(List.of(PromptMessage.user("a reasonably long question about Tokyo"))),
                LlmOptions.defaults())
                .filter(LlmEvent.Usage.class::isInstance).blockFirst();

        assertThat(usage).isNotNull();
        assertThat(usage.inputTokens()).isPositive();
        assertThat(usage.outputTokens()).isPositive();
    }

    /** A stub that invented tool calls would let an orchestrator bug pass its own tests. */
    @Test
    void neverRequestsATool() {
        assertThat(llm.completeWithTools(Prompt.adHoc(List.of(PromptMessage.user("hi"))), List.of(),
                LlmOptions.defaults()).hasToolCalls()).isFalse();
    }

    // ---------------------------------------------------------------------------------------
    // Embeddings
    // ---------------------------------------------------------------------------------------

    @Test
    void producesTheSameVectorForTheSameTextAcrossInstances() {
        assertThat(new StubEmbeddingAdapter(1536).embed("ramen"))
                .containsExactly(new StubEmbeddingAdapter(1536).embed("ramen"));
    }

    @Test
    void producesDifferentVectorsForDifferentTextSoRankingBugsAreVisible() {
        StubEmbeddingAdapter embeddings = new StubEmbeddingAdapter(1536);

        assertThat(embeddings.embed("ramen")).isNotEqualTo(embeddings.embed("temples"));
    }

    /** HNSW with {@code vector_cosine_ops} (ADR 010 §5) expects unit-length vectors. */
    @Test
    void returnsUnitLengthVectors() {
        float[] vector = new StubEmbeddingAdapter(1536).embed("street food");

        double sumOfSquares = 0.0;
        for (float component : vector) {
            sumOfSquares += (double) component * component;
        }

        assertThat(vector).hasSize(1536);
        assertThat(Math.sqrt(sumOfSquares)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    /**
     * Stub rows must be distinguishable in the database from real ones, or a developer's local
     * vectors could end up searched alongside production ones — the mixing ADR 010 §5 forbids.
     */
    @Test
    void neverImpersonatesTheRealEmbeddingModel() {
        assertThat(new StubEmbeddingAdapter(1536).modelRef().model())
                .isEqualTo(StubEmbeddingAdapter.STUB_MODEL)
                .isNotEqualTo("text-embedding-3-small");
    }
}
