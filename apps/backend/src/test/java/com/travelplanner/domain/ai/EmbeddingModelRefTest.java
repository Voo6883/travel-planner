package com.travelplanner.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** ADR 010 §5 — the pin that makes mixing incompatible vectors impossible to express. */
class EmbeddingModelRefTest {

    @Test
    void pinsTheAdr010Model() {
        EmbeddingModelRef pinned = EmbeddingModelRef.pinned();

        assertThat(pinned.provider()).isEqualTo("openai");
        assertThat(pinned.model()).isEqualTo("text-embedding-3-small");
        assertThat(pinned.dimension()).isEqualTo(1536);
    }

    /**
     * The {@code text-embedding-3-*} family accepts a {@code dimensions} parameter, so asking for 512
     * is a legal API call that yields vectors silently incomparable with the index. The type refuses
     * it rather than letting the rows be written.
     */
    @Test
    void rejectsAKnownModelDeclaredWithTheWrongDimension() {
        assertThatThrownBy(() -> new EmbeddingModelRef("openai", "text-embedding-3-small", 512))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1536")
                .hasMessageContaining("ADR 010");
    }

    @Test
    void rejectsTheLargeModelDeclaredAtTheSmallModelsDimension() {
        assertThatThrownBy(() -> new EmbeddingModelRef("openai", "text-embedding-3-large", 1536))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3072");
    }

    @Test
    void allowsAnUnknownModelSoANewProviderDoesNotNeedACodeChangeToBeTried() {
        assertThat(new EmbeddingModelRef("acme", "acme-embed-v1", 768).dimension()).isEqualTo(768);
    }

    /** Same dimension, different space. Nothing would throw at query time — hence the check. */
    @Test
    void treatsTwoProvidersAtTheSameDimensionAsIncompatible() {
        EmbeddingModelRef openAi = new EmbeddingModelRef("openai", "text-embedding-3-small", 1536);
        EmbeddingModelRef stub = new EmbeddingModelRef("stub", "stub-embedding-v1", 1536);

        assertThat(openAi.isCompatibleWith(stub)).isFalse();
        assertThat(openAi.isCompatibleWith(EmbeddingModelRef.pinned())).isTrue();
    }

    @Test
    void namesItselfProviderFirstSoTheEmbeddingModelColumnIsUnambiguous() {
        assertThat(EmbeddingModelRef.pinned().qualifiedName()).isEqualTo("openai:text-embedding-3-small");
    }
}
