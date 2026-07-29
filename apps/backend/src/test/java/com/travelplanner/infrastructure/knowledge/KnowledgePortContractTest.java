package com.travelplanner.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.ai.stub.StubEmbeddingAdapter;
import com.travelplanner.domain.ai.EmbeddingModelRef;
import com.travelplanner.domain.enums.KnowledgeMatchType;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.KnowledgeMatch;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.port.KnowledgePort;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.domain.valueobject.KnowledgeQuery;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Shared {@link KnowledgePort} contract (task 17). Concrete subclasses supply the stub adapter and
 * the pgvector adapter (mocked collaborators) so both honour the same assertions.
 */
public abstract class KnowledgePortContractTest {

    protected abstract KnowledgePort port();

    protected abstract Destination tokyo();

    @Test
    void everyPoiCarriesSampleProvenance() {
        List<Poi> pois = port().findPois(tokyo().id(), Optional.empty());
        assertThat(pois).isNotEmpty();
        assertThat(pois).allSatisfy(poi -> {
            assertThat(poi.provenance().sourceRef()).isEqualTo(KnowledgeProvenance.SAMPLE_SOURCE_REF);
            assertThat(poi.provenance().isSampleData()).isTrue();
            assertThat(poi.provenance().sourceUrl()).isNull();
        });
    }

    @Test
    void supportedDestinationsNeverIncludePartialSampleCities() {
        assertThat(tokyo().coverageLevel().isRankingEligible()).isFalse();
        assertThat(port().findSupportedDestinations()).isEmpty();
    }

    @Test
    void streetFoodSearchSurfacesFoodMatches() {
        List<KnowledgeMatch> matches = search("street food");
        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).snippet().toLowerCase()).containsAnyOf("street food", "food");
        assertThat(matches).allSatisfy(match ->
                assertThat(match.provenance().sourceRef())
                        .isEqualTo(KnowledgeProvenance.SAMPLE_SOURCE_REF));
    }

    @Test
    void templesSearchSurfacesTempleMatches() {
        List<KnowledgeMatch> matches = search("temples");
        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).snippet().toLowerCase()).contains("temple");
    }

    @Test
    void unknownDestinationSearchReturnsEmptyRatherThanInventingHits() {
        KnowledgeQuery query = new KnowledgeQuery(
                UUID.randomUUID(),
                "street food",
                new StubEmbeddingAdapter(EmbeddingModelRef.PINNED_DIMENSION).embed("street food"),
                KnowledgeQuery.DEFAULT_TOP_K,
                0.01,
                Set.of(KnowledgeMatchType.POI));
        assertThat(port().search(query)).isEmpty();
    }

    @Test
    void categoryFilterReturnsOnlyFoodPois() {
        List<Poi> food = port().findPois(tokyo().id(), Optional.of(PoiCategory.FOOD));
        assertThat(food).isNotEmpty();
        assertThat(food).allMatch(poi -> poi.category() == PoiCategory.FOOD);
    }

    protected List<KnowledgeMatch> search(String text) {
        float[] embedding = new StubEmbeddingAdapter(EmbeddingModelRef.PINNED_DIMENSION).embed(text);
        KnowledgeQuery query = new KnowledgeQuery(
                tokyo().id(), text, embedding, KnowledgeQuery.DEFAULT_TOP_K, 0.05,
                Set.of(KnowledgeMatchType.POI, KnowledgeMatchType.GUIDE));
        return port().search(query);
    }
}

class StubDestinationKnowledgeAdapterContractTest extends KnowledgePortContractTest {

    private static StubDestinationKnowledgeAdapter stub;
    private static Destination tokyo;

    @BeforeAll
    static void load() {
        stub = StubDestinationKnowledgeAdapter.fromSampleClasspath();
        tokyo = stub.findDestinationBySlug("tokyo-jp").orElseThrow();
    }

    @Override
    protected KnowledgePort port() {
        return stub;
    }

    @Override
    protected Destination tokyo() {
        return tokyo;
    }

    @Test
    void hybridBeatsPureVectorOnStreetFoodAndTemples() {
        for (String queryText : List.of("street food", "temples")) {
            float[] embedding = new StubEmbeddingAdapter(EmbeddingModelRef.PINNED_DIMENSION)
                    .embed(queryText);
            KnowledgeQuery query = new KnowledgeQuery(
                    tokyo.id(), queryText, embedding, 10, 0.0, Set.of(KnowledgeMatchType.POI));
            List<KnowledgeMatch> hybrid = stub.search(query);
            List<KnowledgeMatch> pure = stub.searchPureVector(query);
            assertThat(hybrid).isNotEmpty();
            String needle = queryText.contains("temple") ? "temple" : "street food";
            assertThat(hybrid.get(0).snippet().toLowerCase()).contains(needle);
            if (!pure.isEmpty()) {
                boolean pureTopIsLexical = pure.get(0).snippet().toLowerCase().contains(needle);
                assertThat(hybrid.get(0).snippet().toLowerCase()).contains(needle);
                if (pureTopIsLexical) {
                    assertThat(hybrid.get(0).score()).isGreaterThanOrEqualTo(pure.get(0).score());
                }
            }
        }
    }
}
