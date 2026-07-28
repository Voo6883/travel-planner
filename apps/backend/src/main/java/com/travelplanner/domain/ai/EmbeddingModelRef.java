package com.travelplanner.domain.ai;

import java.util.Map;
import java.util.Objects;

/**
 * The identity of the embedding model behind a vector index (ADR 010 §5).
 *
 * <p><strong>Why this is a type and not three loose config values.</strong> PLAN §5.4: "Anthropic and
 * OpenAI vectors are not comparable; mixing corrupts search." The corruption is silent — a cosine
 * similarity between vectors from two different models is a perfectly well-formed number that means
 * nothing. There is no query that fails and no exception to catch; retrieval just quietly returns
 * the wrong POIs, and the agent grounds an answer on them.
 *
 * <p>So the model, the dimension, and the provider travel together as one value, and every embedding
 * row records the value that produced it ({@code embedding_model}, {@code embedding_dimension} —
 * ADR 010 §5). A row can then be compared against the active pin before it is ever searched, and a
 * model migration is an additive backfill rather than an in-place rebuild.
 *
 * @param dimension the vector length. Validated against {@link #KNOWN_DIMENSIONS} at startup, so a
 *     hand-edited config claiming {@code text-embedding-3-small} is 3072-dimensional fails to boot
 *     instead of writing unusable rows.
 */
public record EmbeddingModelRef(String provider, String model, int dimension) {

    /** ADR 010 §5 pins this model for v1. Changing it requires an ADR, not a config edit. */
    public static final String PINNED_MODEL = "text-embedding-3-small";

    /** ADR 010 §5 pins this dimension for {@link #PINNED_MODEL}. */
    public static final int PINNED_DIMENSION = 1536;

    /** The provider that serves {@link #PINNED_MODEL}. */
    public static final String PINNED_PROVIDER = "openai";

    /**
     * Model name to its only correct dimension.
     *
     * <p>{@code text-embedding-3-*} models support dimension reduction, which is exactly the trap:
     * asking for 512 dimensions from the pinned model is a legal API call that produces vectors that
     * cannot be compared with the 1536-dimensional ones already in the index. Only the pinned value
     * is accepted.
     */
    private static final Map<String, Integer> KNOWN_DIMENSIONS = Map.of(
            "text-embedding-3-small", 1536,
            "text-embedding-3-large", 3072,
            "text-embedding-ada-002", 1536,
            "stub-embedding-v1", 1536);

    public EmbeddingModelRef {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        if (dimension <= 0) {
            throw new IllegalArgumentException("Embedding dimension must be positive");
        }
        Integer expected = KNOWN_DIMENSIONS.get(model);
        if (expected != null && expected != dimension) {
            throw new IllegalArgumentException(
                    "Embedding model '" + model + "' produces " + expected + "-dimensional vectors, "
                            + "but the configuration says " + dimension
                            + ". Mixing dimensions in one index corrupts retrieval silently "
                            + "(ADR 010 §5).");
        }
    }

    /** The ADR 010 §5 pin. The only value {@code travelplanner.ai.embeddings} may resolve to in v1. */
    public static EmbeddingModelRef pinned() {
        return new EmbeddingModelRef(PINNED_PROVIDER, PINNED_MODEL, PINNED_DIMENSION);
    }

    /**
     * Whether vectors produced under {@code other} may be searched alongside vectors produced under
     * this reference. Provider is part of it: two providers can serve the same dimension and still
     * place points in unrelated spaces.
     */
    public boolean isCompatibleWith(EmbeddingModelRef other) {
        return other != null
                && provider.equals(other.provider)
                && model.equals(other.model)
                && dimension == other.dimension;
    }

    /** Stable identifier for logs and the {@code embedding_model} column. */
    public String qualifiedName() {
        return provider + ":" + model;
    }
}
