package com.travelplanner.infrastructure.persistence;

/**
 * Fuses vector cosine similarity with a Postgres-style text rank (ADR 010 §5).
 *
 * <p>Pure vector search underperforms on the product's own lexical examples ("street food",
 * "temples"). This class is the single definition of the fused score so
 * {@link KnowledgeVectorSearch} (SQL) and {@code StubDestinationKnowledgeAdapter} (in-memory)
 * cannot disagree about what "hybrid" means.
 *
 * <p>Text rank is mapped through {@code r / (1 + r)} so an unbounded {@code ts_rank_cd} (or an
 * in-memory hit count) lands in {@code [0, 1)} before the weights apply. The fused result is then
 * clamped to {@code [0, 1]} so it can be stored on {@code KnowledgeMatch.score}.
 */
public final class HybridRetrievalScore {

    /** Weight on cosine similarity. Slightly above text so an embedding-only hit still ranks. */
    public static final double VECTOR_WEIGHT = 0.55;

    /** Weight on the normalised lexical rank. Enough to overturn a weak vector on exact tokens. */
    public static final double TEXT_WEIGHT = 0.45;

    private HybridRetrievalScore() {
    }

    /**
     * @param vectorSim cosine similarity in {@code 0..1}
     * @param textRank non-negative rank ({@code ts_rank_cd} or an in-memory hit count)
     */
    public static double fuse(double vectorSim, double textRank) {
        requireUnitInterval(vectorSim, "vectorSim");
        if (textRank < 0.0 || Double.isNaN(textRank)) {
            throw new IllegalArgumentException("textRank must be >= 0, got " + textRank);
        }
        double textSim = textRank / (1.0 + textRank);
        return clamp(VECTOR_WEIGHT * vectorSim + TEXT_WEIGHT * textSim);
    }

    /** Pure-vector baseline used in tests that prove hybrid beats it on lexical queries. */
    public static double pureVector(double vectorSim) {
        requireUnitInterval(vectorSim, "vectorSim");
        return vectorSim;
    }

    private static void requireUnitInterval(double value, String name) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be 0.0..1.0, got " + value);
        }
    }

    private static double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
