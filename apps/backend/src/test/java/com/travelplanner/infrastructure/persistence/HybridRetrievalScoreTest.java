package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Proves hybrid fusion beats pure vector on lexical travel queries (ADR 010 §5, task 17 DoD).
 *
 * <p>The numbers mirror what stub embeddings produce: a temple/street-food row can sit below the
 * vector floor of an unrelated POI while still carrying a strong {@code ts_rank}. Fusion is what
 * flips the order.
 */
class HybridRetrievalScoreTest {

    @Test
    void hybridRanksTemplesAboveAStrongerButNonLexicalVectorHit() {
        // Temple POI: weak vector, strong lexical hit on "temples".
        double templeHybrid = HybridRetrievalScore.fuse(0.35, 2.0);
        // Unrelated POI: stronger vector, no lexical hit.
        double unrelatedHybrid = HybridRetrievalScore.fuse(0.72, 0.0);

        assertThat(templeHybrid).isGreaterThan(unrelatedHybrid);
        assertThat(HybridRetrievalScore.pureVector(0.35))
                .isLessThan(HybridRetrievalScore.pureVector(0.72));
    }

    @Test
    void hybridRanksStreetFoodAboveAStrongerButNonLexicalVectorHit() {
        double streetFoodHybrid = HybridRetrievalScore.fuse(0.40, 2.0);
        double unrelatedHybrid = HybridRetrievalScore.fuse(0.80, 0.0);

        assertThat(streetFoodHybrid).isGreaterThan(unrelatedHybrid);
        assertThat(HybridRetrievalScore.pureVector(0.40))
                .isLessThan(HybridRetrievalScore.pureVector(0.80));
    }

    @Test
    void fusedScoreStaysWithinUnitInterval() {
        assertThat(HybridRetrievalScore.fuse(1.0, 100.0)).isBetween(0.0, 1.0);
        assertThat(HybridRetrievalScore.fuse(0.0, 0.0)).isZero();
    }

    @Test
    void rejectsOutOfRangeVectorSimilarity() {
        assertThatThrownBy(() -> HybridRetrievalScore.fuse(1.5, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vectorSim");
        assertThatThrownBy(() -> HybridRetrievalScore.fuse(0.5, -1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("textRank");
    }
}
