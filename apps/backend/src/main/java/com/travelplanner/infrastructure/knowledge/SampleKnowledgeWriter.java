package com.travelplanner.infrastructure.knowledge;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.GuideFieldGroup;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.AreaNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.DestinationNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.GuideNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.PoiNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.PriceObservationNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.RouteSegmentNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.SeasonalityNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.TransportModeNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.TravelAppNode;
import com.travelplanner.infrastructure.persistence.entity.DestinationAreaEntity;
import com.travelplanner.infrastructure.persistence.entity.DestinationEntity;
import com.travelplanner.infrastructure.persistence.entity.DestinationGuideEntity;
import com.travelplanner.infrastructure.persistence.entity.KnowledgeSourceEntity;
import com.travelplanner.infrastructure.persistence.entity.PoiEntity;
import com.travelplanner.infrastructure.persistence.entity.PriceHistoryEntity;
import com.travelplanner.infrastructure.persistence.entity.RouteSegmentEntity;
import com.travelplanner.infrastructure.persistence.entity.SeasonalityEntity;
import com.travelplanner.infrastructure.persistence.entity.TransportModeEntity;
import com.travelplanner.infrastructure.persistence.entity.TravelAppEntity;
import com.travelplanner.infrastructure.persistence.repository.DestinationAreaJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.DestinationGuideJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.DestinationJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.KnowledgeSourceJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.PoiJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.PriceHistoryJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.RouteSegmentJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.SeasonalityJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.TransportModeJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.TravelAppJpaRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns one parsed sample document into catalogue rows and embedding rows.
 *
 * <h2>Idempotence: it creates, it never updates</h2>
 *
 * <p>Every row is looked up first by the natural key its table already declares unique — slug for a
 * destination, area, POI, transport mode and app; {@code (destination, month)} for seasonality;
 * {@code (destination, category, observed_on)} for a price; {@code (from, to, mode)} for a route
 * segment. An existing row is left exactly as it is.
 *
 * <p>Leaving it alone rather than overwriting is the deliberate half. {@code DevAdminSeeder} takes
 * the same position for the same reason: a developer who corrected a guide by hand must not find the
 * correction reverted on the next restart, and task 41's curation UI will write to these very rows.
 * A seeder that reasserted the file's contents on every boot would quietly become the authority over
 * the curator.
 *
 * <p>Embeddings are the one exception, and they are not really one: they are re-checked on every run
 * against {@code content_hash}, so an unchanged chunk costs a {@code SELECT} and nothing else
 * (ADR 010 §5's re-embed trigger), while an emptied embedding table is rebuilt.
 *
 * <h2>Why the flush before embedding</h2>
 *
 * <p>Embedding rows carry a foreign key to the guide or POI they describe, and they are written with
 * {@code JdbcTemplate} rather than JPA. Both share the transaction's connection, but Hibernate has
 * no reason to have flushed its insert yet — so a guide is {@code saveAndFlush}ed, and POIs are
 * flushed as a batch, before {@link SampleEmbeddingWriter} touches the same rows.
 *
 * <h2>Entities, here, outside the persistence package</h2>
 *
 * <p>See this package's {@code package-info}: this is the one place outside
 * {@code infrastructure.persistence} that constructs {@code *Entity} objects. None escapes — every
 * one is handed straight to a repository, and the public methods return counts.
 */
@Component
@Profile({"dev", "docker", "local"})
@ConditionalOnProperty(
        prefix = "travelplanner.knowledge.sample-seed", name = "enabled", havingValue = "true")
@RequiresDatabase
public class SampleKnowledgeWriter {

    private final KnowledgeSourceJpaRepository sources;
    private final DestinationJpaRepository destinations;
    private final DestinationGuideJpaRepository guides;
    private final DestinationAreaJpaRepository areas;
    private final PoiJpaRepository pois;
    private final TransportModeJpaRepository transportModes;
    private final RouteSegmentJpaRepository routeSegments;
    private final SeasonalityJpaRepository seasonality;
    private final PriceHistoryJpaRepository priceHistory;
    private final TravelAppJpaRepository travelApps;
    private final SampleEmbeddingWriter embeddings;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public SampleKnowledgeWriter(
            KnowledgeSourceJpaRepository sources,
            DestinationJpaRepository destinations,
            DestinationGuideJpaRepository guides,
            DestinationAreaJpaRepository areas,
            PoiJpaRepository pois,
            TransportModeJpaRepository transportModes,
            RouteSegmentJpaRepository routeSegments,
            SeasonalityJpaRepository seasonality,
            PriceHistoryJpaRepository priceHistory,
            TravelAppJpaRepository travelApps,
            SampleEmbeddingWriter embeddings) {
        this.sources = sources;
        this.destinations = destinations;
        this.guides = guides;
        this.areas = areas;
        this.pois = pois;
        this.transportModes = transportModes;
        this.routeSegments = routeSegments;
        this.seasonality = seasonality;
        this.priceHistory = priceHistory;
        this.travelApps = travelApps;
        this.embeddings = embeddings;
    }

    /**
     * Creates the reserved sample source if it is absent, and returns whether it created one.
     *
     * <p>Runs in its own transaction so that the source — which every other row's {@code source_id}
     * points at — is committed before the first destination is attempted.
     */
    @Transactional
    public boolean ensureSampleSource(SampleSourceDocument.SourceNode node, Instant now) {
        if (sources.findBySourceRef(node.sourceRef()).isPresent()) {
            return false;
        }
        KnowledgeSourceEntity entity = new KnowledgeSourceEntity();
        entity.setId(UUID.randomUUID());
        entity.setSourceRef(node.sourceRef());
        entity.setName(node.name());
        entity.setLicence(node.licence());
        entity.setAttributionText(node.attributionText());
        // Stays null. ADR 010 §3: sample rows must not cite a plausible-looking URL, and
        // SampleKnowledgeReader has already refused the file if this was populated.
        entity.setSourceUrl(node.sourceUrl());
        entity.setRetrievedAt(node.retrievedAt());
        entity.setTrustTier(node.trustTier());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        sources.save(entity);
        return true;
    }

    /** Seeds one destination and everything hanging off it. */
    @Transactional
    public SeedCounts seed(SampleKnowledgeDocument document, Instant now) {
        KnowledgeSourceEntity source = sources.findBySourceRef(KnowledgeProvenance.SAMPLE_SOURCE_REF)
                .orElseThrow(() -> new IllegalStateException(
                        "The reserved sample source is missing; ensureSampleSource must run first"));

        DestinationNode node = document.destination();
        DestinationEntity destination = destinations.findBySlug(node.slug()).orElse(null);
        SeedContext context = new SeedContext(node.countryCode(), source, now);
        if (destination == null) {
            destination = destinations.save(newDestination(node, now));
            context.created();
        }
        context.destinationId = destination.getId();
        context.destinationSlug = destination.getSlug();

        Map<String, DestinationAreaEntity> areasBySlug = seedAreas(document, context);
        seedGuide(document.guide(), context);
        seedPois(document, areasBySlug, context);
        Map<String, TransportModeEntity> modesBySlug = seedTransportModes(document, context);
        seedRouteSegments(document, areasBySlug, modesBySlug, context);
        seedSeasonality(document, context);
        seedPriceHistory(document, context);
        seedTravelApps(document, context);

        return new SeedCounts(context.rowsCreated, context.chunksEmbedded);
    }

    // -------------------------------------------------------------------------------------
    // destination_area
    // -------------------------------------------------------------------------------------

    private Map<String, DestinationAreaEntity> seedAreas(
            SampleKnowledgeDocument document, SeedContext context) {

        Map<String, DestinationAreaEntity> bySlug = new HashMap<>();
        for (DestinationAreaEntity existing : areas.findByDestinationId(context.destinationId)) {
            bySlug.put(existing.getSlug(), existing);
        }
        for (AreaNode node : document.areas()) {
            if (bySlug.containsKey(node.slug())) {
                continue;
            }
            DestinationAreaEntity entity = new DestinationAreaEntity();
            entity.setId(UUID.randomUUID());
            entity.setDestinationId(context.destinationId);
            entity.setSlug(node.slug());
            entity.setName(node.name());
            entity.setDescription(node.description());
            entity.setLatitude(node.latitude());
            entity.setLongitude(node.longitude());
            entity.setSource(context.source);
            entity.setRetrievedAt(context.now);
            entity.setCreatedAt(context.now);
            entity.setUpdatedAt(context.now);
            bySlug.put(node.slug(), areas.save(entity));
            context.created();
        }
        return bySlug;
    }

    // -------------------------------------------------------------------------------------
    // destination_guide, and its three embedded field groups
    // -------------------------------------------------------------------------------------

    private void seedGuide(GuideNode node, SeedContext context) {
        DestinationGuideEntity guide =
                guides.findByDestinationIdAndLocale(context.destinationId, node.locale())
                        .orElse(null);
        if (guide == null) {
            DestinationGuideEntity entity = new DestinationGuideEntity();
            entity.setId(UUID.randomUUID());
            entity.setDestinationId(context.destinationId);
            entity.setLocale(node.locale());
            entity.setOverview(node.overview());
            entity.setFood(node.food());
            entity.setPractical(node.practical());
            entity.setSource(context.source);
            entity.setRetrievedAt(context.now);
            entity.setCreatedAt(context.now);
            entity.setUpdatedAt(context.now);
            // Flushed rather than saved: the embedding rows below carry a foreign key to this one
            // and are written through JdbcTemplate, which cannot see an unflushed Hibernate insert.
            guide = guides.saveAndFlush(entity);
            context.created();
        }

        // ADR 010 §5: three vectors per guide, never one. `overview`, `food` and `practical` answer
        // different questions, and a single embedding over all three is the unusable centroid the
        // ADR rejects.
        embedGuideChunk(guide.getId(), GuideFieldGroup.OVERVIEW, guide.getOverview(), context);
        embedGuideChunk(guide.getId(), GuideFieldGroup.FOOD, guide.getFood(), context);
        embedGuideChunk(guide.getId(), GuideFieldGroup.PRACTICAL, guide.getPractical(), context);
    }

    private void embedGuideChunk(
            UUID guideId, GuideFieldGroup fieldGroup, String text, SeedContext context) {

        boolean written = embeddings.writeGuideChunk(guideId, context.destinationId,
                context.destinationSlug, fieldGroup, text, context.now);
        if (written) {
            context.embedded();
        }
    }

    // -------------------------------------------------------------------------------------
    // poi, and its single embedded chunk
    // -------------------------------------------------------------------------------------

    private void seedPois(
            SampleKnowledgeDocument document,
            Map<String, DestinationAreaEntity> areasBySlug,
            SeedContext context) {

        Map<String, PoiEntity> bySlug = new HashMap<>();
        for (PoiEntity existing : pois.findByDestinationId(context.destinationId)) {
            bySlug.put(existing.getSlug(), existing);
        }
        boolean anyCreated = false;
        for (PoiNode node : document.pois()) {
            if (bySlug.containsKey(node.slug())) {
                continue;
            }
            PoiEntity entity = new PoiEntity();
            entity.setId(UUID.randomUUID());
            entity.setDestinationId(context.destinationId);
            entity.setAreaId(resolveAreaId(node, areasBySlug, context));
            entity.setSlug(node.slug());
            entity.setName(node.name());
            entity.setDescription(node.description());
            entity.setCategory(node.category());
            entity.setTags(node.tags());
            entity.setLocale(node.locale());
            entity.setLatitude(node.latitude());
            entity.setLongitude(node.longitude());
            entity.setOpeningHours(node.openingHours());
            entity.setPriceBand(node.priceBand());
            entity.setSource(context.source);
            entity.setRetrievedAt(context.now);
            entity.setCreatedAt(context.now);
            entity.setUpdatedAt(context.now);
            bySlug.put(node.slug(), pois.save(entity));
            context.created();
            anyCreated = true;
        }
        if (anyCreated) {
            // One flush for the batch, for the same foreign-key reason as the guide above.
            pois.flush();
        }

        for (PoiNode node : document.pois()) {
            PoiEntity poi = bySlug.get(node.slug());
            String chunk = SampleEmbeddingWriter.poiChunk(
                    poi.getName(), poi.getDescription(), poi.getTags());
            if (embeddings.writePoiChunk(poi.getId(), context.destinationId,
                    context.destinationSlug, chunk, context.now)) {
                context.embedded();
            }
        }
    }

    private UUID resolveAreaId(
            PoiNode node, Map<String, DestinationAreaEntity> areasBySlug, SeedContext context) {

        if (node.areaSlug() == null) {
            return null;
        }
        DestinationAreaEntity area = areasBySlug.get(node.areaSlug());
        if (area == null) {
            throw new IllegalStateException(context.destinationSlug + ": poi '" + node.slug()
                    + "' names area '" + node.areaSlug() + "', which the file does not declare");
        }
        return area.getId();
    }

    // -------------------------------------------------------------------------------------
    // transport_mode and route_segment
    // -------------------------------------------------------------------------------------

    private Map<String, TransportModeEntity> seedTransportModes(
            SampleKnowledgeDocument document, SeedContext context) {

        Map<String, TransportModeEntity> bySlug = new HashMap<>();
        for (TransportModeEntity existing : transportModes.findByDestinationId(context.destinationId)) {
            bySlug.put(existing.getSlug(), existing);
        }
        for (TransportModeNode node : document.transportModes()) {
            if (bySlug.containsKey(node.slug())) {
                continue;
            }
            TransportModeEntity entity = new TransportModeEntity();
            entity.setId(UUID.randomUUID());
            entity.setDestinationId(context.destinationId);
            entity.setSlug(node.slug());
            entity.setName(node.name());
            entity.setKind(node.kind());
            entity.setDescription(node.description());
            entity.setCostBand(node.costBand());
            entity.setTouristFriendly(node.touristFriendly());
            entity.setSource(context.source);
            entity.setRetrievedAt(context.now);
            entity.setCreatedAt(context.now);
            entity.setUpdatedAt(context.now);
            bySlug.put(node.slug(), transportModes.save(entity));
            context.created();
        }
        return bySlug;
    }

    private void seedRouteSegments(
            SampleKnowledgeDocument document,
            Map<String, DestinationAreaEntity> areasBySlug,
            Map<String, TransportModeEntity> modesBySlug,
            SeedContext context) {

        Set<String> existing = new HashSet<>();
        for (RouteSegmentEntity segment : routeSegments.findByDestinationId(context.destinationId)) {
            existing.add(segmentKey(segment.getFromAreaId(), segment.getToAreaId(),
                    segment.getTransportModeId()));
        }
        for (RouteSegmentNode node : document.routeSegments()) {
            UUID from = requireArea(areasBySlug, node.fromAreaSlug(), context);
            UUID to = requireArea(areasBySlug, node.toAreaSlug(), context);
            TransportModeEntity mode = modesBySlug.get(node.transportModeSlug());
            if (mode == null) {
                throw new IllegalStateException(context.destinationSlug + ": route segment names "
                        + "transport mode '" + node.transportModeSlug()
                        + "', which the file does not declare");
            }
            if (!existing.add(segmentKey(from, to, mode.getId()))) {
                continue;
            }
            RouteSegmentEntity entity = new RouteSegmentEntity();
            entity.setId(UUID.randomUUID());
            entity.setDestinationId(context.destinationId);
            entity.setFromAreaId(from);
            entity.setToAreaId(to);
            entity.setTransportModeId(mode.getId());
            entity.setDurationMinutes(node.durationMinutes());
            entity.setEstimated(node.estimated());
            entity.setNotes(node.notes());
            entity.setSource(context.source);
            entity.setRetrievedAt(context.now);
            entity.setCreatedAt(context.now);
            entity.setUpdatedAt(context.now);
            routeSegments.save(entity);
            context.created();
        }
    }

    private UUID requireArea(
            Map<String, DestinationAreaEntity> areasBySlug, String slug, SeedContext context) {

        DestinationAreaEntity area = areasBySlug.get(slug);
        if (area == null) {
            throw new IllegalStateException(context.destinationSlug + ": route segment names area '"
                    + slug + "', which the file does not declare");
        }
        return area.getId();
    }

    private static String segmentKey(UUID from, UUID to, UUID mode) {
        return from + "|" + to + "|" + mode;
    }

    // -------------------------------------------------------------------------------------
    // seasonality, price_history, travel_app
    // -------------------------------------------------------------------------------------

    private void seedSeasonality(SampleKnowledgeDocument document, SeedContext context) {
        Set<Short> months = new HashSet<>();
        for (SeasonalityEntity existing : seasonality.findByDestinationId(context.destinationId)) {
            months.add(existing.getMonth());
        }
        for (SeasonalityNode node : document.seasonality()) {
            if (!months.add((short) node.month())) {
                continue;
            }
            SeasonalityEntity entity = new SeasonalityEntity();
            entity.setId(UUID.randomUUID());
            entity.setDestinationId(context.destinationId);
            entity.setMonth((short) node.month());
            entity.setWeatherBand(node.weatherBand());
            entity.setCrowdBand(node.crowdBand());
            entity.setPriceBand(node.priceBand());
            entity.setNotes(node.notes());
            entity.setSource(context.source);
            entity.setRetrievedAt(context.now);
            entity.setCreatedAt(context.now);
            entity.setUpdatedAt(context.now);
            seasonality.save(entity);
            context.created();
        }
    }

    private void seedPriceHistory(SampleKnowledgeDocument document, SeedContext context) {
        Set<String> categories = new LinkedHashSet<>();
        for (PriceObservationNode node : document.priceHistory()) {
            categories.add(node.category());
        }
        Set<String> existing = new HashSet<>();
        for (String category : categories) {
            priceHistory
                    .findByDestinationIdAndCategoryOrderByObservedOnDesc(
                            context.destinationId, category)
                    .forEach(row -> existing.add(row.getCategory() + "|" + row.getObservedOn()));
        }
        for (PriceObservationNode node : document.priceHistory()) {
            if (!existing.add(node.category() + "|" + node.observedOn())) {
                continue;
            }
            PriceHistoryEntity entity = new PriceHistoryEntity();
            entity.setId(UUID.randomUUID());
            entity.setDestinationId(context.destinationId);
            entity.setCategory(node.category());
            entity.setAmount(node.amount());
            entity.setCurrency(node.currency());
            entity.setObservedOn(node.observedOn());
            entity.setSource(context.source);
            entity.setRetrievedAt(context.now);
            // No updatedAt: an observation is a fact about a month, not a row that gets edited.
            entity.setCreatedAt(context.now);
            priceHistory.save(entity);
            context.created();
        }
    }

    private void seedTravelApps(SampleKnowledgeDocument document, SeedContext context) {
        Set<String> existing = new HashSet<>();
        for (TravelAppEntity app : travelApps.findByCountryCode(context.countryCode)) {
            existing.add(app.getSlug());
        }
        for (TravelAppNode node : document.travelApps()) {
            if (!context.countryCode.equals(node.countryCode())) {
                // Apps are country-scoped, and a mismatched code would seed a pack no destination
                // in this file can reach — a copy-paste slip that is otherwise invisible.
                throw new IllegalStateException(context.destinationSlug + ": travel app '"
                        + node.slug() + "' declares country '" + node.countryCode()
                        + "' but the destination is in '" + context.countryCode + "'");
            }
            if (!existing.add(node.slug())) {
                continue;
            }
            TravelAppEntity entity = new TravelAppEntity();
            entity.setId(UUID.randomUUID());
            entity.setCountryCode(node.countryCode());
            entity.setSlug(node.slug());
            entity.setName(node.name());
            entity.setCategory(node.category());
            entity.setDescription(node.description());
            entity.setIosUrl(node.iosUrl());
            entity.setAndroidUrl(node.androidUrl());
            entity.setSource(context.source);
            entity.setRetrievedAt(context.now);
            entity.setCreatedAt(context.now);
            entity.setUpdatedAt(context.now);
            travelApps.save(entity);
            context.created();
        }
    }

    private static DestinationEntity newDestination(DestinationNode node, Instant now) {
        DestinationEntity entity = new DestinationEntity();
        entity.setId(UUID.randomUUID());
        entity.setSlug(node.slug());
        entity.setName(node.name());
        entity.setCountryCode(node.countryCode());
        entity.setTimezone(node.timezone());
        entity.setLatitude(node.latitude());
        entity.setLongitude(node.longitude());
        // PARTIAL in every sample file, and SampleKnowledgeReader refuses one that says FULL.
        entity.setCoverageLevel(node.coverageLevel());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    /** What one destination's seed did, for the startup log. */
    public record SeedCounts(int rowsCreated, int chunksEmbedded) {
    }

    /**
     * The values every {@code seed*} method needs, plus the running totals.
     *
     * <p>A mutable carrier rather than eleven repeated parameters. It never escapes a single
     * {@link #seed} call, so there is no shared state between destinations or between threads.
     */
    private static final class SeedContext {

        private final String countryCode;
        private final KnowledgeSourceEntity source;
        private final Instant now;

        private UUID destinationId;
        private String destinationSlug;
        private int rowsCreated;
        private int chunksEmbedded;

        private SeedContext(String countryCode, KnowledgeSourceEntity source, Instant now) {
            this.countryCode = countryCode;
            this.source = source;
            this.now = now;
        }

        private void created() {
            rowsCreated++;
        }

        private void embedded() {
            chunksEmbedded++;
        }
    }
}
