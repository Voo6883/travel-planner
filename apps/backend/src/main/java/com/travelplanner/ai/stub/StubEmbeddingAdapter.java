package com.travelplanner.ai.stub;

import com.travelplanner.domain.ai.EmbeddingModelRef;
import com.travelplanner.domain.port.EmbeddingPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Random;

/**
 * Deterministic embeddings with no credentials — the default so the knowledge tasks (16, 17) can be
 * built and tested before anyone provisions an OpenAI key.
 *
 * <p><strong>Same text, same vector, forever.</strong> The vector is seeded from a SHA-256 of the
 * input, so it is stable across processes and machines. That is what makes it usable: a random
 * vector would make every retrieval test flaky, and a constant vector would make every similarity
 * identical and hide ranking bugs. Seeded-by-content gives stable, distinct, unit-length vectors
 * whose similarities are meaningless but reproducible.
 *
 * <p>It reports its own model name, {@code stub-embedding-v1}, rather than impersonating
 * {@code text-embedding-3-small}. Rows written under the stub therefore carry a visibly different
 * {@code embedding_model} and can never be silently searched alongside real ones — which is exactly
 * the mixing ADR 010 §5 forbids. Same 1536 dimensions, so the schema and index are exercised for
 * real.
 */
public final class StubEmbeddingAdapter implements EmbeddingPort {

    /** Never {@code text-embedding-3-small}: stub vectors must be distinguishable in the database. */
    public static final String STUB_MODEL = "stub-embedding-v1";

    private final EmbeddingModelRef modelRef;

    public StubEmbeddingAdapter(int dimension) {
        this.modelRef = new EmbeddingModelRef("stub", STUB_MODEL, dimension);
    }

    @Override
    public EmbeddingModelRef modelRef() {
        return modelRef;
    }

    @Override
    public float[] embed(String text) {
        Random seeded = new Random(seedOf(text == null ? "" : text));
        float[] vector = new float[modelRef.dimension()];
        double sumOfSquares = 0.0;
        for (int index = 0; index < vector.length; index++) {
            vector[index] = (float) seeded.nextGaussian();
            sumOfSquares += (double) vector[index] * vector[index];
        }
        // Unit length, because cosine similarity over unnormalised vectors and the HNSW
        // vector_cosine_ops index (ADR 010 §5) disagree in ways that only show up as bad ranking.
        double norm = Math.sqrt(sumOfSquares);
        if (norm > 0.0) {
            for (int index = 0; index < vector.length; index++) {
                vector[index] = (float) (vector[index] / norm);
            }
        }
        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    private static long seedOf(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            long seed = 0L;
            for (int index = 0; index < Long.BYTES; index++) {
                seed = (seed << 8) | (digest[index] & 0xFFL);
            }
            return seed;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every JVM", impossible);
        }
    }
}
