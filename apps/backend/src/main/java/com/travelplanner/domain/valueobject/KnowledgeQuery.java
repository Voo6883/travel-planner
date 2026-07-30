package com.travelplanner.domain.valueobject;

import com.travelplanner.domain.enums.KnowledgeMatchType;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A retrieval request against the TKB (ADR 010 §5).
 *
 * <p><strong>{@code destinationId} is mandatory, and that is the whole design.</strong> ADR 010 §5
 * requires the destination filter to be applied <em>before</em> the ANN search, because
 * post-filtering an HNSW result destroys recall: the index returns its global top-k and the filter
 * then discards most of it, so a destination with few rows can come back empty while relevant rows
 * sit unreturned. Making the field non-null means no caller can accidentally issue the unscoped
 * query that behaves this way — the schema's per-destination partial indexes have a matching
 * predicate to hit.
 *
 * <p>Both {@code text} and {@code embedding} are carried because retrieval is hybrid. ADR 010 §5
 * fuses vector similarity with Postgres full-text search, on the grounds that the plan's own worked
 * examples ("street food", "temples") are lexical and underperform under pure vector search.
 *
 * @param embedding the query vector. Copied defensively — a caller reusing a scratch buffer would
 *        otherwise mutate a query already in flight
 * @param topK how many candidates to retrieve before reranking
 * @param similarityFloor cosine similarity below which a match is not worth returning at all. A
 *        floor matters more than the ranking here: without one, an empty corpus still returns
 *        twenty confident-looking rows
 */
public record KnowledgeQuery(
        UUID destinationId,
        String text,
        float[] embedding,
        int topK,
        double similarityFloor,
        Set<KnowledgeMatchType> types) {

    /** ADR 010 §5. */
    public static final int DEFAULT_TOP_K = 20;

    /** ADR 010 §5. */
    public static final double DEFAULT_SIMILARITY_FLOOR = 0.5;

    /** ADR 010 §5 pins the embedding model to 1536 dimensions. */
    public static final int EMBEDDING_DIMENSION = 1536;

    public KnowledgeQuery {
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(embedding, "embedding");
        Objects.requireNonNull(types, "types");

        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        // A wrong-length vector is rejected here rather than by the database, so the failure names
        // the query instead of surfacing as a constraint violation three layers down.
        if (embedding.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException(
                    "embedding must have " + EMBEDDING_DIMENSION + " dimensions, got " + embedding.length);
        }
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be at least 1, got " + topK);
        }
        if (similarityFloor < 0.0 || similarityFloor > 1.0) {
            throw new IllegalArgumentException(
                    "similarityFloor must be within 0.0..1.0, got " + similarityFloor);
        }
        if (types.isEmpty()) {
            throw new IllegalArgumentException("at least one KnowledgeMatchType must be requested");
        }
        embedding = embedding.clone();
        types = Set.copyOf(types);
    }

    /** A query over both guides and POIs using the ADR's defaults. */
    public static KnowledgeQuery of(UUID destinationId, String text, float[] embedding) {
        return new KnowledgeQuery(destinationId, text, embedding, DEFAULT_TOP_K,
                DEFAULT_SIMILARITY_FLOOR, Set.of(KnowledgeMatchType.values()));
    }

    /**
     * Defensive copy on the way out as well as in.
     *
     * <p>A record's generated accessor would hand back the internal array, so the immutability the
     * compact constructor established would last exactly until the first caller read it.
     */
    @Override
    public float[] embedding() {
        return embedding.clone();
    }

    /**
     * Value equality, including the vector.
     *
     * <p>A record's generated {@code equals} compares {@code float[]} by reference, so two queries
     * built from identical inputs are unequal and one built from the same array is equal to itself
     * and nothing else. That is not a style question here: task 37 keys the semantic cache on this
     * type, and reference equality would make every cache lookup a miss — the cache would look
     * present, cost nothing to maintain, and never hit. The same defect makes {@code hashCode}
     * identity-based, which is the half that silently breaks {@code HashMap}.
     *
     * <p>{@link java.util.Arrays#equals(float[], float[])} rather than element-wise {@code ==}:
     * it treats {@code NaN} as equal to itself and {@code 0.0f} as distinct from {@code -0.0f},
     * which is what a cache key needs — reflexivity first, arithmetic sense second.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof KnowledgeQuery query
                && topK == query.topK
                && Double.compare(similarityFloor, query.similarityFloor) == 0
                && destinationId.equals(query.destinationId)
                && text.equals(query.text)
                && java.util.Arrays.equals(embedding, query.embedding)
                && types.equals(query.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(destinationId, text, java.util.Arrays.hashCode(embedding), topK,
                similarityFloor, types);
    }

    /**
     * Excludes the vector.
     *
     * <p>The generated form prints {@code [F@1b6d3586}, which is noise, and a 1536-float dump would
     * be worse — it is the one field that makes a log line unreadable and tells nobody anything.
     */
    @Override
    public String toString() {
        return "KnowledgeQuery[destinationId=" + destinationId + ", text='" + text + "', embedding="
                + embedding.length + "d, topK=" + topK + ", similarityFloor=" + similarityFloor
                + ", types=" + types + "]";
    }
}
