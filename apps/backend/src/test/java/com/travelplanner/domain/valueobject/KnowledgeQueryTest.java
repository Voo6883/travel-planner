package com.travelplanner.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.enums.KnowledgeMatchType;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class KnowledgeQueryTest {

    private static final UUID DESTINATION_ID = UUID.randomUUID();

    @Test
    void appliesTheAdrDefaultsWhenBuiltThroughTheConvenienceFactory() {
        KnowledgeQuery query = KnowledgeQuery.of(DESTINATION_ID, "street food",
                KnowledgeFixtures.embedding());

        assertThat(query.destinationId()).isEqualTo(DESTINATION_ID);
        assertThat(query.text()).isEqualTo("street food");
        assertThat(query.topK()).isEqualTo(KnowledgeQuery.DEFAULT_TOP_K).isEqualTo(20);
        assertThat(query.similarityFloor())
                .isEqualTo(KnowledgeQuery.DEFAULT_SIMILARITY_FLOOR)
                .isEqualTo(0.5);
        assertThat(query.types()).containsExactlyInAnyOrder(
                KnowledgeMatchType.GUIDE, KnowledgeMatchType.POI);
        assertThat(query.embedding()).hasSize(KnowledgeQuery.EMBEDDING_DIMENSION);
    }

    @Test
    void copiesTheCallersEmbeddingSoAReusedScratchBufferCannotRewriteAQueryInFlight() {
        float[] callerBuffer = KnowledgeFixtures.embedding();
        KnowledgeQuery query = KnowledgeQuery.of(DESTINATION_ID, "temples", callerBuffer);

        callerBuffer[0] = 99f;
        callerBuffer[1] = 99f;

        assertThat(query.embedding()[0]).isEqualTo(0.25f);
        assertThat(query.embedding()[1]).isZero();
    }

    @Test
    void handsBackACopyOfTheEmbeddingSoAReaderCannotMutateTheQueryThroughIt() {
        // Without the accessor override, the record's generated one would return the internal
        // array and the copy made on construction would protect nothing past the first read.
        KnowledgeQuery query = KnowledgeQuery.of(DESTINATION_ID, "temples",
                KnowledgeFixtures.embedding());

        float[] borrowed = query.embedding();
        borrowed[0] = 99f;

        assertThat(query.embedding()[0]).isEqualTo(0.25f);
        assertThat(query.embedding()).isNotSameAs(borrowed);
    }

    @Test
    void copiesTheRequestedTypesAndExposesThemAsAnUnmodifiableSet() {
        Set<KnowledgeMatchType> callerTypes = EnumSet.of(KnowledgeMatchType.POI);
        KnowledgeQuery query = new KnowledgeQuery(DESTINATION_ID, "ramen",
                KnowledgeFixtures.embedding(), 5, 0.6, callerTypes);

        callerTypes.add(KnowledgeMatchType.GUIDE);

        assertThat(query.types()).containsExactly(KnowledgeMatchType.POI);
        assertThatThrownBy(() -> query.types().add(KnowledgeMatchType.GUIDE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n"})
    void rejectsAQueryWithNoLexicalHalf(String blank) {
        // Retrieval is hybrid: the text side carries the lexical match that pure vector search
        // underperforms on, so a blank text is half a query rather than a broad one.
        assertThatThrownBy(() -> KnowledgeQuery.of(DESTINATION_ID, blank,
                KnowledgeFixtures.embedding()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text must not be blank");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 1535, 1537, 3072})
    void rejectsAnEmbeddingThatIsNotThePinnedDimension(int wrongLength) {
        assertThatThrownBy(() -> KnowledgeQuery.of(DESTINATION_ID, "temples", new float[wrongLength]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1536 dimensions");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsATopKThatCouldNeverReturnACandidate(int topK) {
        assertThatThrownBy(() -> new KnowledgeQuery(DESTINATION_ID, "temples",
                KnowledgeFixtures.embedding(), topK, 0.5, Set.of(KnowledgeMatchType.POI)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK must be at least 1");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.0001, -1.0, 1.0001, 2.0})
    void rejectsASimilarityFloorOutsideTheCosineRange(double floor) {
        assertThatThrownBy(() -> new KnowledgeQuery(DESTINATION_ID, "temples",
                KnowledgeFixtures.embedding(), 20, floor, Set.of(KnowledgeMatchType.POI)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("similarityFloor must be within 0.0..1.0");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.5, 1.0})
    void acceptsBothEndsOfTheSimilarityRange(double floor) {
        KnowledgeQuery query = new KnowledgeQuery(DESTINATION_ID, "temples",
                KnowledgeFixtures.embedding(), 1, floor, Set.of(KnowledgeMatchType.GUIDE));

        assertThat(query.similarityFloor()).isEqualTo(floor);
        assertThat(query.topK()).isEqualTo(1);
    }

    @Test
    void rejectsARequestForNoMatchTypesAtAll() {
        assertThatThrownBy(() -> new KnowledgeQuery(DESTINATION_ID, "temples",
                KnowledgeFixtures.embedding(), 20, 0.5, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one KnowledgeMatchType");
    }

    @Test
    void refusesTheUnscopedQueryThatWouldPostFilterAnAnnResult() {
        // destinationId is non-null precisely so this call cannot be written by accident: ADR 010
        // §5 requires the destination filter before the ANN search, not after it.
        assertThatThrownBy(() -> KnowledgeQuery.of(null, "temples", KnowledgeFixtures.embedding()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsTheOtherRequiredFieldsBeingAbsent() {
        float[] embedding = KnowledgeFixtures.embedding();

        assertThatThrownBy(() -> KnowledgeQuery.of(DESTINATION_ID, null, embedding))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> KnowledgeQuery.of(DESTINATION_ID, "temples", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KnowledgeQuery(DESTINATION_ID, "temples", embedding, 20, 0.5, null))
                .isInstanceOf(NullPointerException.class);
    }
}
