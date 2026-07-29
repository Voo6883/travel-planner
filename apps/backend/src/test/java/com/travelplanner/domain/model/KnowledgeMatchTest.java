package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.enums.KnowledgeMatchType;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class KnowledgeMatchTest {

    private static final KnowledgeProvenance PROVENANCE = KnowledgeFixtures.provenance();

    @Test
    void carriesTheScoreAndTheCitationSoNeitherNeedsARejoin() {
        // The step that gets skipped is always the citation, so provenance travels with the hit
        // rather than being fetched from the row it came from.
        UUID id = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        KnowledgeMatch match = new KnowledgeMatch(KnowledgeMatchType.POI, id, destinationId,
                "Stalls and knife shops.", 0.82, PROVENANCE);

        assertThat(match.sourceType()).isEqualTo(KnowledgeMatchType.POI);
        assertThat(match.id()).isEqualTo(id);
        assertThat(match.destinationId()).isEqualTo(destinationId);
        assertThat(match.snippet()).isEqualTo("Stalls and knife shops.");
        assertThat(match.score()).isEqualTo(0.82);
        assertThat(match.provenance()).isEqualTo(PROVENANCE);
    }

    @ParameterizedTest
    @EnumSource(KnowledgeMatchType.class)
    void namesWhichTableItsIdRefersToBecauseTheIdAloneIsAmbiguous(KnowledgeMatchType sourceType) {
        assertThat(match(sourceType, "a snippet", 0.5).sourceType()).isEqualTo(sourceType);
    }

    @Test
    void rejectsANaNScoreOnItsOwnBecauseARangeCheckWouldWaveItThrough() {
        // Every comparison against NaN is false, so `score < 0.0 || score > 1.0` returns false for
        // NaN and it would reach the reranker, where it sorts unpredictably against real scores.
        assertThatThrownBy(() -> match(KnowledgeMatchType.POI, "a snippet", Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score must be a number");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.0001, -1.0, 1.0001, 2.0,
        Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsAScoreOutsideTheCosineSimilarityRange(double score) {
        assertThatThrownBy(() -> match(KnowledgeMatchType.GUIDE, "a snippet", score))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score must be 0.0..1.0");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.5, 1.0})
    void acceptsBothEndsOfTheCosineSimilarityRange(double score) {
        assertThat(match(KnowledgeMatchType.GUIDE, "a snippet", score).score()).isEqualTo(score);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void rejectsABlankSnippetBecauseAMatchWithNothingToShowIsNotAMatch(String blank) {
        assertThatThrownBy(() -> match(KnowledgeMatchType.POI, blank, 0.9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snippet must not be blank");
    }

    @Test
    void rejectsEveryRequiredFieldBeingAbsent() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new KnowledgeMatch(null, id, id, "snippet", 0.5, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KnowledgeMatch(KnowledgeMatchType.POI, null, id, "snippet",
                0.5, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KnowledgeMatch(KnowledgeMatchType.POI, id, null, "snippet",
                0.5, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KnowledgeMatch(KnowledgeMatchType.POI, id, id, null,
                0.5, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KnowledgeMatch(KnowledgeMatchType.POI, id, id, "snippet",
                0.5, null)).isInstanceOf(NullPointerException.class);
    }

    private static KnowledgeMatch match(KnowledgeMatchType sourceType, String snippet, double score) {
        return new KnowledgeMatch(sourceType, UUID.randomUUID(), UUID.randomUUID(), snippet,
                score, PROVENANCE);
    }
}
