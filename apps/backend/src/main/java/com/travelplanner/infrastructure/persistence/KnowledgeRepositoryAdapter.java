package com.travelplanner.infrastructure.persistence;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.domain.model.DestinationGuide;
import com.travelplanner.domain.model.KnowledgeMatch;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.PriceObservation;
import com.travelplanner.domain.model.RouteSegment;
import com.travelplanner.domain.model.SeasonalityMonth;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.domain.port.KnowledgePort;
import com.travelplanner.domain.valueobject.KnowledgeQuery;
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
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@link KnowledgePort} implementation (PLAN §4.0.2-I, ADR 010).
 *
 * <p>Every method returns domain types built by an explicit MapStruct mapper — no
 * {@code *Entity} crosses this boundary, per {@code package-info.java}. That matters more here
 * than elsewhere: a {@code PoiEntity} carries a lazy {@code source} association, and handing one
 * out would turn a citation into a {@code LazyInitializationException} at render time.
 *
 * <p><strong>Read-only transactions.</strong> The whole port is annotated
 * {@code @Transactional(readOnly = true)}. The TKB is read on every planning request and written
 * only by tasks 40 and 41, so flagging it lets Hibernate skip dirty-checking on the returned
 * graph. It also means the lazy {@code source} association is resolved inside an open session,
 * which is what the {@code join fetch} in each repository query is there to make cheap.
 *
 * <p><strong>Search is not JPA.</strong> {@link #search(KnowledgeQuery)} delegates to
 * {@link KnowledgeVectorSearch}, which issues native SQL because pgvector's {@code <=>} operator
 * has no JPQL equivalent. The translation this class performs is
 * {@code destinationId -> destinationSlug}: the query object is keyed by id, while V18's partial
 * HNSW indexes are predicated on the slug, and only a slug lets the planner pick the right index.
 */
@Component
@RequiresDatabase
@Transactional(readOnly = true)
public class KnowledgeRepositoryAdapter implements KnowledgePort {

    private final DestinationJpaRepository destinations;
    private final DestinationGuideJpaRepository guides;
    private final DestinationAreaJpaRepository areas;
    private final PoiJpaRepository pois;
    private final TransportModeJpaRepository transportModes;
    private final RouteSegmentJpaRepository routeSegments;
    private final TravelAppJpaRepository travelApps;
    private final SeasonalityJpaRepository seasonality;
    private final PriceHistoryJpaRepository priceHistory;

    private final DestinationPersistenceMapper destinationMapper;
    private final DestinationGuidePersistenceMapper guideMapper;
    private final DestinationAreaPersistenceMapper areaMapper;
    private final PoiPersistenceMapper poiMapper;
    private final TransportModePersistenceMapper transportModeMapper;
    private final RouteSegmentPersistenceMapper routeSegmentMapper;
    private final TravelAppPersistenceMapper travelAppMapper;
    private final SeasonalityMonthPersistenceMapper seasonalityMapper;
    private final PriceObservationPersistenceMapper priceObservationMapper;

    private final KnowledgeVectorSearch vectorSearch;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public KnowledgeRepositoryAdapter(
            DestinationJpaRepository destinations,
            DestinationGuideJpaRepository guides,
            DestinationAreaJpaRepository areas,
            PoiJpaRepository pois,
            TransportModeJpaRepository transportModes,
            RouteSegmentJpaRepository routeSegments,
            TravelAppJpaRepository travelApps,
            SeasonalityJpaRepository seasonality,
            PriceHistoryJpaRepository priceHistory,
            DestinationPersistenceMapper destinationMapper,
            DestinationGuidePersistenceMapper guideMapper,
            DestinationAreaPersistenceMapper areaMapper,
            PoiPersistenceMapper poiMapper,
            TransportModePersistenceMapper transportModeMapper,
            RouteSegmentPersistenceMapper routeSegmentMapper,
            TravelAppPersistenceMapper travelAppMapper,
            SeasonalityMonthPersistenceMapper seasonalityMapper,
            PriceObservationPersistenceMapper priceObservationMapper,
            KnowledgeVectorSearch vectorSearch) {
        this.destinations = destinations;
        this.guides = guides;
        this.areas = areas;
        this.pois = pois;
        this.transportModes = transportModes;
        this.routeSegments = routeSegments;
        this.travelApps = travelApps;
        this.seasonality = seasonality;
        this.priceHistory = priceHistory;
        this.destinationMapper = destinationMapper;
        this.guideMapper = guideMapper;
        this.areaMapper = areaMapper;
        this.poiMapper = poiMapper;
        this.transportModeMapper = transportModeMapper;
        this.routeSegmentMapper = routeSegmentMapper;
        this.travelAppMapper = travelAppMapper;
        this.seasonalityMapper = seasonalityMapper;
        this.priceObservationMapper = priceObservationMapper;
        this.vectorSearch = vectorSearch;
    }

    @Override
    public Optional<Destination> findDestinationBySlug(String slug) {
        return destinations.findBySlug(slug).map(destinationMapper::toDomain);
    }

    @Override
    public Optional<Destination> findDestinationById(UUID destinationId) {
        return destinations.findById(destinationId).map(destinationMapper::toDomain);
    }

    @Override
    public List<Destination> findSupportedDestinations() {
        // ADR 010 §4: "supported" means FULL, not "present in the table". A PARTIAL destination
        // listed here would be offered to the user and then refused by requireRankable.
        return destinations.findByCoverageLevel(CoverageLevel.FULL).stream()
                .map(destinationMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<DestinationGuide> findGuide(UUID destinationId, String locale) {
        return guides.findByDestinationIdAndLocale(destinationId, locale).map(guideMapper::toDomain);
    }

    @Override
    public List<DestinationArea> findAreas(UUID destinationId) {
        return areas.findByDestinationId(destinationId).stream().map(areaMapper::toDomain).toList();
    }

    @Override
    public List<Poi> findPois(UUID destinationId, Optional<PoiCategory> category) {
        return category
                .map(value -> pois.findByDestinationIdAndCategory(destinationId, value))
                .orElseGet(() -> pois.findByDestinationId(destinationId))
                .stream()
                .map(poiMapper::toDomain)
                .toList();
    }

    @Override
    public List<TransportMode> findTransportModes(UUID destinationId) {
        return transportModes.findByDestinationId(destinationId).stream()
                .map(transportModeMapper::toDomain)
                .toList();
    }

    @Override
    public List<RouteSegment> findRouteSegments(UUID destinationId) {
        return routeSegments.findByDestinationId(destinationId).stream()
                .map(routeSegmentMapper::toDomain)
                .toList();
    }

    @Override
    public List<TravelApp> findTravelApps(String countryCode) {
        return travelApps.findByCountryCode(countryCode).stream()
                .map(travelAppMapper::toDomain)
                .toList();
    }

    @Override
    public List<SeasonalityMonth> findSeasonality(UUID destinationId) {
        return seasonality.findByDestinationId(destinationId).stream()
                .map(seasonalityMapper::toDomain)
                .toList();
    }

    @Override
    public List<PriceObservation> findPriceHistory(UUID destinationId, String category) {
        return priceHistory.findByDestinationIdAndCategoryOrderByObservedOnDesc(destinationId, category)
                .stream()
                .map(priceObservationMapper::toDomain)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>An unknown {@code destinationId} yields an empty list rather than an exception. The
     * coverage refusal belongs to the caller, which holds the {@link Destination} and can raise
     * {@code destination_not_covered} with the supported list attached; a repository that threw
     * here would be inventing an answer it lacks the context to give.
     */
    @Override
    public List<KnowledgeMatch> search(KnowledgeQuery query) {
        return destinations.findById(query.destinationId())
                .map(destination -> vectorSearch.search(query, destination.getSlug()))
                .orElseGet(List::of);
    }
}
