package com.travelplanner.infrastructure.knowledge;

import com.travelplanner.infrastructure.persistence.HybridRetrievalScore;
import java.util.Locale;

/**
 * In-memory stand-in for {@code ts_rank_cd} + {@link HybridRetrievalScore}, shared by
 * {@link StubDestinationKnowledgeAdapter} so its ranking contract matches the SQL path.
 */
final class LexicalHybridScorer {

    private LexicalHybridScorer() {
    }

    /** Count of non-blank query tokens that appear as substrings in {@code document}. */
    static double textRank(String query, String document) {
        if (query == null || document == null || query.isBlank() || document.isBlank()) {
            return 0.0;
        }
        String haystack = document.toLowerCase(Locale.ROOT);
        double hits = 0.0;
        for (String token : query.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+")) {
            if (!token.isBlank() && haystack.contains(token)) {
                hits += 1.0;
            }
        }
        return hits;
    }

    static double fuse(float[] queryEmbedding, float[] docEmbedding, String query, String document) {
        return HybridRetrievalScore.fuse(cosine(queryEmbedding, docEmbedding), textRank(query, document));
    }

    static double pureVector(float[] queryEmbedding, float[] docEmbedding) {
        return HybridRetrievalScore.pureVector(cosine(queryEmbedding, docEmbedding));
    }

    /** Cosine similarity clamped to {@code 0..1} for {@link KnowledgeMatch#score}. */
    static double cosine(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("embedding length mismatch");
        }
        double dot = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
        }
        if (dot < 0.0) {
            return 0.0;
        }
        if (dot > 1.0) {
            return 1.0;
        }
        return dot;
    }
}
