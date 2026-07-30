package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.KnowledgeMatchType;
import com.travelplanner.domain.enums.TrustTier;
import com.travelplanner.domain.model.KnowledgeMatch;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The fusion arithmetic, without a database.
 *
 * <p>Reciprocal rank fusion is the part of hybrid retrieval most likely to be "simplified" by someone
 * who reads the two SQL queries, sees a merge, and assumes it is a sort. These tests state the
 * properties that make it not a sort — and each one corresponds to a way score-level fusion fails.
 *
 * @see KnowledgeHybridSearch
 */
class KnowledgeHybridSearchTest {

    private static final int K = KnowledgeHybridSearch.RANK_CONSTANT;

    /** Agreement between the arms is the signal fusion exists to reward. */
    @Test
    void aDocumentBothArmsRankFirstScoresOne() {
        KnowledgeMatch shared = poi("shared");

        List<KnowledgeMatch> fused = KnowledgeHybridSearch.fuse(
                List.of(List.of(shared), List.of(shared)), 2, 10);

        assertThat(fused).singleElement()
                .satisfies(match -> assertThat(match.relevance()).isEqualTo(1.0));
    }

    /**
     * First place in one arm and absent from the other is exactly half.
     *
     * <p>The number is worth pinning because it is the one a caller is most likely to filter away. A
     * lexical-only hit is the <em>only</em> kind of result hybrid retrieval can add over pure vector,
     * and it arrives at 0.5 — the same value as ADR 010 §5's cosine floor. A caller that confuses the
     * two drops precisely the rows fusion was added to surface, and the result still looks plausible.
     */
    @Test
    void aDocumentOnlyOneArmFoundScoresExactlyHalf() {
        List<KnowledgeMatch> fused = KnowledgeHybridSearch.fuse(
                List.of(List.of(poi("vector-only")), List.of()), 2, 10);

        assertThat(fused).singleElement()
                .satisfies(match -> assertThat(match.relevance()).isEqualTo(0.5));
    }

    /**
     * Consensus outranks a single strong hit, but not by much.
     *
     * <p>This is the trade {@code K = 60} encodes. A document at rank 3 in both arms beats one at rank
     * 1 in a single arm — {@code 2/63 > 1/61} — which is the intended behaviour: two independent
     * retrieval strategies agreeing is stronger evidence than one being confident. At {@code K = 0} the
     * comparison inverts, which is why the constant is not arbitrary.
     */
    @Test
    void agreementAtRankThreeBeatsFirstPlaceInOneArmAlone() {
        KnowledgeMatch consensus = poi("consensus");
        KnowledgeMatch loner = poi("loner");
        List<KnowledgeMatch> vector = List.of(loner, poi("filler-a"), consensus);
        List<KnowledgeMatch> lexical = List.of(poi("filler-b"), poi("filler-c"), consensus);

        List<KnowledgeMatch> fused = KnowledgeHybridSearch.fuse(List.of(vector, lexical), 2, 10);

        assertThat(fused.get(0).id()).isEqualTo(consensus.id());
        assertThat(fused).extracting(KnowledgeMatch::id).contains(loner.id());
    }

    /**
     * The scores each arm produced are discarded entirely.
     *
     * <p>The property that makes fusion stable. Both arms here rank the same two documents in the same
     * order, but with wildly different score scales — cosine 0.9/0.6 against a normalised
     * {@code ts_rank} of 0.05/0.04. A weighted sum would let the vector arm's larger numbers dominate
     * regardless of ranking; RRF cannot see them, so the outcome depends only on position.
     */
    @Test
    void theArmsOwnScoresDoNotAffectTheOutcome() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        List<KnowledgeMatch> fusedFromWideScores = KnowledgeHybridSearch.fuse(List.of(
                List.of(poi(first, 0.9), poi(second, 0.6)),
                List.of(poi(first, 0.05), poi(second, 0.04))), 2, 10);
        List<KnowledgeMatch> fusedFromNarrowScores = KnowledgeHybridSearch.fuse(List.of(
                List.of(poi(first, 0.51), poi(second, 0.50)),
                List.of(poi(first, 0.99), poi(second, 0.98))), 2, 10);

        assertThat(fusedFromWideScores).extracting(KnowledgeMatch::id).containsExactly(first, second);
        assertThat(fusedFromNarrowScores).extracting(KnowledgeMatch::relevance)
                .containsExactlyElementsOf(
                        fusedFromWideScores.stream().map(KnowledgeMatch::relevance).toList());
    }

    /**
     * Adding an irrelevant row does not reorder rows that did not move.
     *
     * <p>The specific instability that rules out every normaliser derived from the returned set —
     * divide-by-max, min-max, z-score. Under those, one extra row at the bottom of one arm rescales
     * everything above it. Here the two originals keep both their order and their exact relevance.
     */
    @Test
    void anExtraLowRankedRowDoesNotDisturbTheRowsAboveIt() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<KnowledgeMatch> vector = List.of(poi(first, 0.9), poi(second, 0.8));

        List<KnowledgeMatch> before = KnowledgeHybridSearch.fuse(
                List.of(vector, List.of(poi(first, 0.1))), 2, 10);
        List<KnowledgeMatch> after = KnowledgeHybridSearch.fuse(
                List.of(vector, List.of(poi(first, 0.1), poi(UUID.randomUUID(), 0.01))), 2, 10);

        assertThat(after).hasSize(3);
        assertThat(after.subList(0, 2)).extracting(KnowledgeMatch::id, KnowledgeMatch::relevance)
                .containsExactlyElementsOf(before.stream()
                        .map(match -> org.assertj.core.groups.Tuple.tuple(match.id(), match.relevance()))
                        .toList());
    }

    /**
     * A guide-only query has one arm, and its scores are not inflated to look like agreement.
     *
     * <p>The lexical arm is POI-only by design, so a {@code GUIDE}-only request genuinely has one arm.
     * Dividing by "arms that answered" would score its top hit 1.0 — unanimous — when only one arm was
     * ever asked. Dividing by arms *consulted* keeps 1.0 meaning what it means everywhere else.
     */
    @Test
    void oneArmConsultedStillPutsItsTopHitAtOne() {
        List<KnowledgeMatch> fused = KnowledgeHybridSearch.fuse(
                List.of(List.of(guide("overview")), List.of()), 1, 10);

        assertThat(fused).singleElement()
                .satisfies(match -> assertThat(match.relevance()).isEqualTo(1.0));
    }

    /** Two arms consulted, one silent — the same list scores half, because agreement is missing. */
    @Test
    void theSameListScoresHalfWhenASecondArmWasConsultedAndFoundNothing() {
        List<KnowledgeMatch> onlyArmAsked = KnowledgeHybridSearch.fuse(
                List.of(List.of(poi("a")), List.of()), 1, 10);
        List<KnowledgeMatch> bothArmsAsked = KnowledgeHybridSearch.fuse(
                List.of(List.of(poi("a")), List.of()), 2, 10);

        assertThat(onlyArmAsked.get(0).relevance()).isEqualTo(1.0);
        assertThat(bothArmsAsked.get(0).relevance()).isEqualTo(0.5);
    }

    /**
     * A POI and a guide with the same id are two documents.
     *
     * <p>{@code id} is a primary key within its own table, so the two UUID spaces overlap in principle.
     * Keying on {@code (sourceType, id)} costs nothing and removes the question — and a collision that
     * did happen would merge two unrelated rows into one match with the wrong snippet.
     */
    @Test
    void aPoiAndAGuideSharingAnIdAreNotTheSameDocument() {
        UUID collision = UUID.randomUUID();

        List<KnowledgeMatch> fused = KnowledgeHybridSearch.fuse(
                List.of(List.of(poi(collision, 0.9), guide(collision, 0.8)), List.of()), 2, 10);

        assertThat(fused).hasSize(2)
                .extracting(KnowledgeMatch::sourceType)
                .containsExactly(KnowledgeMatchType.POI, KnowledgeMatchType.GUIDE);
    }

    @Test
    void truncatesToTopK() {
        List<KnowledgeMatch> arm = List.of(poi("a"), poi("b"), poi("c"), poi("d"));

        assertThat(KnowledgeHybridSearch.fuse(List.of(arm, List.of()), 2, 2)).hasSize(2);
    }

    /**
     * Ties resolve by the order the first arm produced, not by hash.
     *
     * <p>On a ten-POI corpus ties are the norm, and a {@code HashMap} would break them by the hash of a
     * random UUID — a ranking that changes between runs for reasons nothing in the data explains, which
     * is unassertable and therefore untestable.
     */
    @Test
    void tiesKeepTheFirstArmsOrderRatherThanAHashOrder() {
        List<KnowledgeMatch> arm = List.of(poi("first"), poi("second"), poi("third"));

        // Every document appears once, at a distinct rank, so no two share a fused score — but run the
        // same input twice and the order must be identical, which a hash-ordered map would not give.
        List<KnowledgeMatch> once = KnowledgeHybridSearch.fuse(List.of(arm, List.of()), 2, 10);
        List<KnowledgeMatch> twice = KnowledgeHybridSearch.fuse(List.of(arm, List.of()), 2, 10);

        assertThat(once).extracting(KnowledgeMatch::id)
                .containsExactlyElementsOf(twice.stream().map(KnowledgeMatch::id).toList())
                .containsExactlyElementsOf(arm.stream().map(KnowledgeMatch::id).toList());
    }

    /**
     * The relevance stays inside {@link KnowledgeMatch}'s range under floating-point drift.
     *
     * <p>{@code (1/61 + 1/61) / (2 * 1/61)} is 1.0 in exact arithmetic and can land a hair above it in
     * binary floating point, which the record rejects. The clamp lives in the fusion rather than in the
     * record because an out-of-range value arriving from anywhere else is a bug worth failing on.
     */
    @Test
    void doesNotProduceARelevanceAboveOne() {
        KnowledgeMatch shared = poi("shared");

        assertThat(KnowledgeHybridSearch.fuse(List.of(List.of(shared), List.of(shared)), 2, 10))
                .allSatisfy(match -> assertThat(match.relevance()).isBetween(0.0, 1.0));
    }

    @Test
    void everyArmEmptyYieldsNoMatches() {
        assertThat(KnowledgeHybridSearch.fuse(List.of(List.of(), List.of()), 2, 10)).isEmpty();
    }

    /** The constant is the paper's, and the tests above depend on its value. */
    @Test
    void theRankConstantIsSixty() {
        assertThat(K).isEqualTo(60);
    }

    // ---------------------------------------------------------------------------------------

    private static KnowledgeMatch poi(String label) {
        return poi(UUID.nameUUIDFromBytes(label.getBytes()), 0.7);
    }

    private static KnowledgeMatch poi(UUID id, double relevance) {
        return match(KnowledgeMatchType.POI, id, relevance);
    }

    private static KnowledgeMatch guide(String label) {
        return guide(UUID.nameUUIDFromBytes(label.getBytes()), 0.7);
    }

    private static KnowledgeMatch guide(UUID id, double relevance) {
        return match(KnowledgeMatchType.GUIDE, id, relevance);
    }

    private static KnowledgeMatch match(KnowledgeMatchType type, UUID id, double relevance) {
        return new KnowledgeMatch(type, id, UUID.nameUUIDFromBytes("tokyo".getBytes()),
                "snippet for " + id, relevance,
                new KnowledgeProvenance(KnowledgeProvenance.SAMPLE_SOURCE_REF, "Sample",
                        KnowledgeLicence.SAMPLE_DATA, "Sample data", null, TrustTier.SAMPLE,
                        Instant.EPOCH));
    }
}
