package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.KnowledgeMatchType;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.KnowledgeMatch;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.port.KnowledgePort;
import com.travelplanner.domain.valueobject.KnowledgeQuery;
import com.travelplanner.infrastructure.knowledge.KnowledgePortContractTest;
import com.travelplanner.infrastructure.knowledge.StubDestinationKnowledgeAdapter;
import com.travelplanner.infrastructure.persistence.entity.DestinationEntity;
import com.travelplanner.infrastructure.persistence.entity.PoiEntity;
import com.travelplanner.infrastructure.persistence.mapper.DestinationAreaPersistenceMapper;
import com.travelplanner.infrastructure.persistence.mapper.DestinationGuidePersistenceMapper;
import com.travelplanner.infrastructure.persistence.mapper.DestinationPersistenceMapper;
import com.travelplanner.infrastructure.persistence.mapper.PoiPersistenceMapper;
import com.travelplanner.infrastructure.persistence.mapper.PriceObservationPersistenceMapper;
import com.travelplanner.infrastructure.persistence.mapper.RouteSegmentPersistenceMapper;
import com.travelplanner.infrastructure.persistence.mapper.SeasonalityMonthPersistenceMapper;
import com.travelplanner.infrastructure.persistence.mapper.TransportModePersistenceMapper;
import com.travelplanner.infrastructure.persistence.mapper.TravelAppPersistenceMapper;
import com.travelplanner.infrastructure.persistence.repository.DestinationAreaJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.DestinationGuideJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.DestinationJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.PoiJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.PriceHistoryJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.RouteSegmentJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.SeasonalityJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.TransportModeJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.TravelAppJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Shared port contract for {@link PgVectorKnowledgeAdapter} (task 17).
 *
 * <p>Collaborators are mocked so {@code ./gradlew test} stays Docker-free. Search delegates to
 * {@link KnowledgeVectorSearch}; hybrid SQL itself is covered by {@code HybridRetrievalScoreTest}
 * and the stub corpus. Live Postgres round-trips remain F-45.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PgVectorKnowledgeAdapterContractTest extends KnowledgePortContractTest {

    private static StubDestinationKnowledgeAdapter corpus;
    private static Destination tokyo;

    @Mock private DestinationJpaRepository destinations;
    @Mock private DestinationGuideJpaRepository guides;
    @Mock private DestinationAreaJpaRepository areas;
    @Mock private PoiJpaRepository pois;
    @Mock private TransportModeJpaRepository transportModes;
    @Mock private RouteSegmentJpaRepository routeSegments;
    @Mock private TravelAppJpaRepository travelApps;
    @Mock private SeasonalityJpaRepository seasonality;
    @Mock private PriceHistoryJpaRepository priceHistory;
    @Mock private DestinationPersistenceMapper destinationMapper;
    @Mock private DestinationGuidePersistenceMapper guideMapper;
    @Mock private DestinationAreaPersistenceMapper areaMapper;
    @Mock private PoiPersistenceMapper poiMapper;
    @Mock private TransportModePersistenceMapper transportModeMapper;
    @Mock private RouteSegmentPersistenceMapper routeSegmentMapper;
    @Mock private TravelAppPersistenceMapper travelAppMapper;
    @Mock private SeasonalityMonthPersistenceMapper seasonalityMapper;
    @Mock private PriceObservationPersistenceMapper priceObservationMapper;
    @Mock private KnowledgeVectorSearch vectorSearch;

    private PgVectorKnowledgeAdapter adapter;

    @BeforeAll
    static void loadCorpus() {
        corpus = StubDestinationKnowledgeAdapter.fromSampleClasspath();
        tokyo = corpus.findDestinationBySlug("tokyo-jp").orElseThrow();
    }

    @BeforeEach
    void wireAdapter() {
        adapter = new PgVectorKnowledgeAdapter(
                destinations, guides, areas, pois, transportModes, routeSegments, travelApps,
                seasonality, priceHistory, destinationMapper, guideMapper, areaMapper, poiMapper,
                transportModeMapper, routeSegmentMapper, travelAppMapper, seasonalityMapper,
                priceObservationMapper, vectorSearch);
        stubStructuredReads();
    }

    @Override
    protected KnowledgePort port() {
        return adapter;
    }

    @Override
    protected Destination tokyo() {
        return tokyo;
    }

    @Test
    void searchResolvesSlugThenDelegatesToHybridVectorSearch() {
        DestinationEntity entity = new DestinationEntity();
        entity.setId(tokyo.id());
        entity.setSlug(tokyo.slug());
        when(destinations.findById(tokyo.id())).thenReturn(Optional.of(entity));
        KnowledgeMatch hit = corpus.search(new KnowledgeQuery(
                tokyo.id(), "temples",
                com.travelplanner.domain.KnowledgeFixtures.embedding(),
                5, 0.05, Set.of(KnowledgeMatchType.POI))).get(0);
        when(vectorSearch.search(any(KnowledgeQuery.class), eq(tokyo.slug())))
                .thenReturn(List.of(hit));

        List<KnowledgeMatch> matches = search("temples");

        assertThat(matches).containsExactly(hit);
        verify(vectorSearch).search(any(KnowledgeQuery.class), eq(tokyo.slug()));
    }

    private void stubStructuredReads() {
        when(destinations.findByCoverageLevel(CoverageLevel.FULL)).thenReturn(List.of());
        List<Poi> domainPois = corpus.findPois(tokyo.id(), Optional.empty());
        List<PoiEntity> entities = domainPois.stream().map(poi -> {
            PoiEntity entity = new PoiEntity();
            entity.setId(poi.id());
            return entity;
        }).toList();
        when(pois.findByDestinationId(tokyo.id())).thenReturn(entities);
        when(pois.findByDestinationIdAndCategory(tokyo.id(), PoiCategory.FOOD)).thenReturn(
                domainPois.stream()
                        .filter(poi -> poi.category() == PoiCategory.FOOD)
                        .map(poi -> {
                            PoiEntity entity = new PoiEntity();
                            entity.setId(poi.id());
                            return entity;
                        })
                        .toList());
        when(poiMapper.toDomain(any(PoiEntity.class))).thenAnswer(invocation -> {
            PoiEntity entity = invocation.getArgument(0);
            return domainPois.stream()
                    .filter(poi -> poi.id().equals(entity.getId()))
                    .findFirst()
                    .orElseThrow();
        });
        when(destinations.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            if (!id.equals(tokyo.id())) {
                return Optional.empty();
            }
            DestinationEntity entity = new DestinationEntity();
            entity.setId(tokyo.id());
            entity.setSlug(tokyo.slug());
            return Optional.of(entity);
        });
        when(vectorSearch.search(any(KnowledgeQuery.class), eq(tokyo.slug()))).thenAnswer(inv -> {
            KnowledgeQuery query = inv.getArgument(0);
            return corpus.search(query);
        });
    }
}
