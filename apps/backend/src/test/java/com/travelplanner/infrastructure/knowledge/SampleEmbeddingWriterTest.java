package com.travelplanner.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The chunking and hashing contract task 40 has to reproduce.
 *
 * <p>ADR 010 §5's re-embed trigger is a {@code content_hash} comparison, so the exact bytes handed to
 * the embedder are part of the seed format, not an implementation detail. If task 40 concatenates a
 * POI differently it will re-embed the entire corpus on its first run and pay for it — which is
 * precisely what the hash exists to avoid. These tests pin the concatenation.
 */
class SampleEmbeddingWriterTest {

    @Test
    void embedsAPoiAsNameDescriptionAndTagsInOneChunk() {
        String chunk = SampleEmbeddingWriter.poiChunk(
                "Sample Riverside Market",
                "A riverside street food market.",
                List.of("street food", "market"));

        assertThat(chunk).isEqualTo(
                "Sample Riverside Market\nA riverside street food market.\nstreet food, market");
    }

    @Test
    void skipsAbsentPartsRatherThanRenderingThemAsNull() {
        assertThat(SampleEmbeddingWriter.poiChunk("Sample Station", null, List.of()))
                .isEqualTo("Sample Station");
        assertThat(SampleEmbeddingWriter.poiChunk("Sample Station", "  ", null))
                .isEqualTo("Sample Station");
    }

    @Test
    void hashesToTheSixtyFourCharactersContentHashHolds() {
        String hash = SampleEmbeddingWriter.sha256("Sample Riverside Market");

        // char(64) in V18: a hash of any other length would be padded or rejected.
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hash).isEqualTo(SampleEmbeddingWriter.sha256("Sample Riverside Market"));
        assertThat(hash).isNotEqualTo(SampleEmbeddingWriter.sha256("Sample Riverside Markets"));
    }

    @Test
    void writesAVectorLiteralPostgresCanParse() {
        assertThat(SampleEmbeddingWriter.toVectorLiteral(new float[] {0.5f, -0.25f, 0.0f}))
                // Never a locale-sensitive formatter: `0,5` would only fail on machines set to a
                // locale that writes it that way.
                .isEqualTo("[0.5,-0.25,0.0]");
    }
}
