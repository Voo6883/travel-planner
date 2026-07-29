package com.travelplanner.infrastructure.knowledge;

import com.travelplanner.ai.stub.StubEmbeddingAdapter;
import com.travelplanner.domain.ai.EmbeddingModelRef;
import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.KnowledgeMatchType;
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
import com.travelplanner.domain.port.EmbeddingPort;
import com.travelplanner.domain.port.KnowledgePort;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.domain.valueobject.KnowledgeQuery;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.AreaNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.GuideNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.PoiNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.PriceObservationNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.RouteSegmentNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.SeasonalityNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.TransportModeNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.TravelAppNode;
import com.travelplanner.infrastructure.knowledge.SampleSourceDocument.SourceNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory {@link KnowledgePort} over the classpath sample seed (ADR 010 §3).
 *
 * <p>Dev/CI only. Every fact cites {@code stub:sample}. Production refuses to start if this bean is
 * wired ({@code KnowledgeConfigValidator}). Not a Spring component by default — construct it in
 * contract tests, or enable {@code travelplanner.knowledge.stub-adapter.enabled=true} when no
 * database adapter is present.
 *
 * <p>Search uses the same {@link com.travelplanner.infrastructure.persistence.HybridRetrievalScore}
 * fusion as {@code KnowledgeVectorSearch}, with {@link StubEmbeddingAdapter} vectors and an
 * in-memory lexical rank so "street food" / "temples" contracts run without Postgres.
 */
public final class StubDestinationKnowledgeAdapter implements KnowledgePort {

    private final Map<UUID, Destination> byId = new HashMap<>();
    private final Map<String, Destination> bySlug = new HashMap<>();
    private final Map<UUID, DestinationGuide> guides = new HashMap<>();
    private final Map<UUID, List<DestinationArea>> areasByDest = new HashMap<>();
    private final Map<UUID, List<Poi>> poisByDest = new HashMap<>();
    private final Map<UUID, List<TransportMode>> modesByDest = new HashMap<>();
    private final Map<UUID, List<RouteSegment>> routesByDest = new HashMap<>();
    private final Map<UUID, List<SeasonalityMonth>> seasonalityByDest = new HashMap<>();
    private final Map<UUID, List<PriceObservation>> pricesByDest = new HashMap<>();
    private final Map<String, List<TravelApp>> appsByCountry = new HashMap<>();
    private final List<SearchableChunk> chunks = new ArrayList<>();
    private final EmbeddingPort embeddings;

    /** Loads the three ADR 010 §1 destinations from {@code knowledge/sample/}. */
    public static StubDestinationKnowledgeAdapter fromSampleClasspath() {
        return fromSampleClasspath(Instant.parse("2026-07-29T00:00:00Z"));
    }

    public static StubDestinationKnowledgeAdapter fromSampleClasspath(Instant seededAt) {
        SampleKnowledgeReader reader = new SampleKnowledgeReader();
        SourceNode source = reader.readSampleSource(seededAt);
        KnowledgeProvenance provenance = toProvenance(source);
        EmbeddingPort embeddings = new StubEmbeddingAdapter(EmbeddingModelRef.PINNED_DIMENSION);
        StubDestinationKnowledgeAdapter adapter = new StubDestinationKnowledgeAdapter(embeddings);
        for (String slug : SampleKnowledgeReader.DESTINATION_SLUGS) {
            adapter.load(reader.readDestination(slug), provenance);
        }
        return adapter;
    }

    StubDestinationKnowledgeAdapter(EmbeddingPort embeddings) {
        this.embeddings = embeddings;
    }

    @Override
    public Optional<Destination> findDestinationBySlug(String slug) {
        return Optional.ofNullable(bySlug.get(slug));
    }

    @Override
    public Optional<Destination> findDestinationById(UUID destinationId) {
        return Optional.ofNullable(byId.get(destinationId));
    }

    @Override
    public List<Destination> findSupportedDestinations() {
        return byId.values().stream()
                .filter(destination -> destination.coverageLevel() == CoverageLevel.FULL)
                .sorted(Comparator.comparing(Destination::slug))
                .toList();
    }

    @Override
    public Optional<DestinationGuide> findGuide(UUID destinationId, String locale) {
        return Optional.ofNullable(guides.get(destinationId))
                .filter(guide -> guide.locale().equals(locale));
    }

    @Override
    public List<DestinationArea> findAreas(UUID destinationId) {
        return List.copyOf(areasByDest.getOrDefault(destinationId, List.of()));
    }

    @Override
    public List<Poi> findPois(UUID destinationId, Optional<PoiCategory> category) {
        List<Poi> all = poisByDest.getOrDefault(destinationId, List.of());
        return category.map(value -> all.stream().filter(poi -> poi.category() == value).toList())
                .orElseGet(() -> List.copyOf(all));
    }

    @Override
    public List<TransportMode> findTransportModes(UUID destinationId) {
        return List.copyOf(modesByDest.getOrDefault(destinationId, List.of()));
    }

    @Override
    public List<RouteSegment> findRouteSegments(UUID destinationId) {
        return List.copyOf(routesByDest.getOrDefault(destinationId, List.of()));
    }

    @Override
    public List<TravelApp> findTravelApps(String countryCode) {
        return List.copyOf(appsByCountry.getOrDefault(countryCode, List.of()));
    }

    @Override
    public List<SeasonalityMonth> findSeasonality(UUID destinationId) {
        return List.copyOf(seasonalityByDest.getOrDefault(destinationId, List.of()));
    }

    @Override
    public List<PriceObservation> findPriceHistory(UUID destinationId, String category) {
        return pricesByDest.getOrDefault(destinationId, List.of()).stream()
                .filter(price -> price.category().equals(category))
                .toList();
    }

    @Override
    public List<KnowledgeMatch> search(KnowledgeQuery query) {
        if (!byId.containsKey(query.destinationId())) {
            return List.of();
        }
        float[] queryVector = query.embedding();
        List<KnowledgeMatch> matches = new ArrayList<>();
        for (SearchableChunk chunk : chunks) {
            if (!chunk.destinationId.equals(query.destinationId())) {
                continue;
            }
            if (!query.types().contains(chunk.type)) {
                continue;
            }
            double score = LexicalHybridScorer.fuse(
                    queryVector, chunk.embedding, query.text(), chunk.searchText);
            if (score >= query.similarityFloor()) {
                matches.add(new KnowledgeMatch(
                        chunk.type, chunk.id, chunk.destinationId, chunk.snippet, score, chunk.provenance));
            }
        }
        return matches.stream()
                .sorted(Comparator.comparingDouble(KnowledgeMatch::score).reversed())
                .limit(query.topK())
                .toList();
    }

    /**
     * Pure-vector ranking of the same corpus — test-only baseline proving hybrid beats it on
     * lexical queries such as {@code street food} and {@code temples}.
     */
    List<KnowledgeMatch> searchPureVector(KnowledgeQuery query) {
        float[] queryVector = query.embedding();
        List<KnowledgeMatch> matches = new ArrayList<>();
        for (SearchableChunk chunk : chunks) {
            if (!chunk.destinationId.equals(query.destinationId())
                    || !query.types().contains(chunk.type)) {
                continue;
            }
            double score = LexicalHybridScorer.pureVector(queryVector, chunk.embedding);
            if (score >= query.similarityFloor()) {
                matches.add(new KnowledgeMatch(
                        chunk.type, chunk.id, chunk.destinationId, chunk.snippet, score, chunk.provenance));
            }
        }
        return matches.stream()
                .sorted(Comparator.comparingDouble(KnowledgeMatch::score).reversed())
                .limit(query.topK())
                .toList();
    }

    private void load(SampleKnowledgeDocument document, KnowledgeProvenance provenance) {
        Destination destination = toDestination(document);
        byId.put(destination.id(), destination);
        bySlug.put(destination.slug(), destination);
        DestGraph graph = new DestGraph(destination.id(), provenance);
        DestinationGuide guide = toGuide(document.guide(), graph);
        guides.put(destination.id(), guide);
        indexGuideChunks(guide);
        loadAreas(document.areas(), graph);
        loadPois(document.pois(), graph);
        loadModes(document.transportModes(), graph);
        loadRoutes(document.routeSegments(), graph);
        loadSeasonality(document.seasonality(), graph);
        loadPrices(document.priceHistory(), graph);
        loadApps(document.travelApps(), provenance);
    }

    private Destination toDestination(SampleKnowledgeDocument document) {
        var node = document.destination();
        return new Destination(
                UUID.randomUUID(),
                node.slug(),
                node.name(),
                node.countryCode(),
                node.timezone(),
                toDouble(node.latitude()),
                toDouble(node.longitude()),
                node.coverageLevel());
    }

    private DestinationGuide toGuide(GuideNode node, DestGraph graph) {
        return new DestinationGuide(
                UUID.randomUUID(), graph.destinationId, node.locale(), node.overview(), node.food(),
                node.practical(), graph.provenance, 0);
    }

    private void indexGuideChunks(DestinationGuide guide) {
        addGuideChunk(guide, guide.overview());
        addGuideChunk(guide, guide.food());
        addGuideChunk(guide, guide.practical());
    }

    private void addGuideChunk(DestinationGuide guide, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        chunks.add(new SearchableChunk(
                KnowledgeMatchType.GUIDE,
                guide.id(),
                guide.destinationId(),
                text,
                text,
                embeddings.embed(text),
                guide.provenance()));
    }

    private void loadAreas(List<AreaNode> nodes, DestGraph graph) {
        List<DestinationArea> areas = new ArrayList<>();
        for (AreaNode node : nodes) {
            UUID id = UUID.randomUUID();
            graph.areaIds.put(node.slug(), id);
            areas.add(new DestinationArea(
                    id, graph.destinationId, node.slug(), node.name(), node.description(),
                    toDouble(node.latitude()), toDouble(node.longitude()), graph.provenance));
        }
        areasByDest.put(graph.destinationId, List.copyOf(areas));
    }

    private void loadPois(List<PoiNode> nodes, DestGraph graph) {
        List<Poi> pois = new ArrayList<>();
        for (PoiNode node : nodes) {
            pois.add(toPoi(node, graph));
        }
        poisByDest.put(graph.destinationId, List.copyOf(pois));
    }

    private Poi toPoi(PoiNode node, DestGraph graph) {
        UUID id = UUID.randomUUID();
        UUID areaId = node.areaSlug() == null ? null : graph.areaIds.get(node.areaSlug());
        Poi poi = new Poi(
                id, graph.destinationId, areaId, node.slug(), node.name(), node.description(),
                node.category(), node.tags(), node.locale(), toDouble(node.latitude()),
                toDouble(node.longitude()), node.openingHours(), node.priceBand(), graph.provenance, 0);
        String chunk = SampleEmbeddingWriter.poiChunk(node.name(), node.description(), node.tags());
        String snippet = node.description() == null || node.description().isBlank()
                ? node.name()
                : node.name() + " — " + node.description();
        chunks.add(new SearchableChunk(
                KnowledgeMatchType.POI, id, graph.destinationId, snippet, chunk,
                embeddings.embed(chunk), graph.provenance));
        return poi;
    }

    private void loadModes(List<TransportModeNode> nodes, DestGraph graph) {
        List<TransportMode> modes = new ArrayList<>();
        for (TransportModeNode node : nodes) {
            UUID id = UUID.randomUUID();
            graph.modeIds.put(node.slug(), id);
            modes.add(new TransportMode(
                    id, graph.destinationId, node.slug(), node.name(), node.kind(), node.description(),
                    node.costBand(), node.touristFriendly(), graph.provenance));
        }
        modesByDest.put(graph.destinationId, List.copyOf(modes));
    }

    private void loadRoutes(List<RouteSegmentNode> nodes, DestGraph graph) {
        List<RouteSegment> routes = new ArrayList<>();
        for (RouteSegmentNode node : nodes) {
            routes.add(new RouteSegment(
                    UUID.randomUUID(),
                    graph.destinationId,
                    graph.areaIds.get(node.fromAreaSlug()),
                    graph.areaIds.get(node.toAreaSlug()),
                    graph.modeIds.get(node.transportModeSlug()),
                    Duration.ofMinutes(node.durationMinutes()),
                    node.estimated(),
                    node.notes(),
                    graph.provenance));
        }
        routesByDest.put(graph.destinationId, List.copyOf(routes));
    }

    private void loadSeasonality(List<SeasonalityNode> nodes, DestGraph graph) {
        List<SeasonalityMonth> months = new ArrayList<>();
        for (SeasonalityNode node : nodes) {
            months.add(new SeasonalityMonth(
                    UUID.randomUUID(), graph.destinationId, node.month(), node.weatherBand(),
                    node.crowdBand(), node.priceBand(), node.notes(), graph.provenance));
        }
        seasonalityByDest.put(graph.destinationId, List.copyOf(months));
    }

    private void loadPrices(List<PriceObservationNode> nodes, DestGraph graph) {
        List<PriceObservation> prices = new ArrayList<>();
        for (PriceObservationNode node : nodes) {
            prices.add(new PriceObservation(
                    UUID.randomUUID(),
                    graph.destinationId,
                    node.category(),
                    Money.of(node.amount(), java.util.Currency.getInstance(node.currency())),
                    node.observedOn(),
                    graph.provenance));
        }
        pricesByDest.put(graph.destinationId, List.copyOf(prices));
    }

    private void loadApps(List<TravelAppNode> nodes, KnowledgeProvenance provenance) {
        for (TravelAppNode node : nodes) {
            TravelApp app = new TravelApp(
                    UUID.randomUUID(), node.countryCode(), node.slug(), node.name(), node.category(),
                    node.description(), node.iosUrl(), node.androidUrl(), provenance);
            appsByCountry
                    .computeIfAbsent(node.countryCode(), key -> new ArrayList<>())
                    .add(app);
        }
    }

    private static KnowledgeProvenance toProvenance(SourceNode source) {
        return new KnowledgeProvenance(
                source.sourceRef(),
                source.name(),
                source.licence(),
                source.attributionText(),
                source.sourceUrl(),
                source.trustTier(),
                source.retrievedAt());
    }

    private static Double toDouble(java.math.BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static final class DestGraph {
        private final UUID destinationId;
        private final KnowledgeProvenance provenance;
        private final Map<String, UUID> areaIds = new HashMap<>();
        private final Map<String, UUID> modeIds = new HashMap<>();

        private DestGraph(UUID destinationId, KnowledgeProvenance provenance) {
            this.destinationId = destinationId;
            this.provenance = provenance;
        }
    }

    private record SearchableChunk(
            KnowledgeMatchType type,
            UUID id,
            UUID destinationId,
            String snippet,
            String searchText,
            float[] embedding,
            KnowledgeProvenance provenance) {
    }
}
