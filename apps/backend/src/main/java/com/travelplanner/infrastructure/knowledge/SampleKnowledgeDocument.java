package com.travelplanner.infrastructure.knowledge;

import com.travelplanner.domain.enums.AppReplacementReason;
import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.CrowdBand;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.TransportKind;
import com.travelplanner.domain.enums.TravelAppCategory;
import com.travelplanner.domain.enums.WeatherBand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Parsed shape of one {@code knowledge/sample/<slug>.json} file.
 *
 * <p>The record tree mirrors migrations V13–V17 one node per table, and is documented for authors in
 * {@code src/main/resources/knowledge/sample/README.md}. Two properties of the format are worth
 * restating here because they are easy to lose in a refactor:
 *
 * <ul>
 *   <li><strong>Everything is referenced by slug.</strong> {@code areaSlug},
 *       {@code transportModeSlug}, {@code fromAreaSlug}, {@code toAreaSlug} and {@code sourceRef}
 *       are natural keys; no file holds a UUID. Ids are generated at seed time, so a re-seed against
 *       an empty database produces different ids and the same graph.</li>
 *   <li><strong>Coordinates are {@link BigDecimal}, not {@code double}.</strong> The columns are
 *       {@code numeric(9,6)}, and a {@code double} would round-trip through binary floating point
 *       and change the stored scale — the same reason the entities use {@code BigDecimal}.</li>
 * </ul>
 *
 * <p>Jackson binds these records by constructor parameter name (the build passes
 * {@code -parameters}) with a snake_case naming strategy, and with unknown properties rejected: a
 * typo in a seed file is a startup failure rather than a silently missing column.
 */
public record SampleKnowledgeDocument(
        int formatVersion,
        DestinationNode destination,
        GuideNode guide,
        List<AreaNode> areas,
        List<PoiNode> pois,
        List<TransportModeNode> transportModes,
        List<RouteSegmentNode> routeSegments,
        List<SeasonalityNode> seasonality,
        List<PriceObservationNode> priceHistory,
        List<TravelAppNode> travelApps) {

    public SampleKnowledgeDocument {
        areas = copyOrEmpty(areas);
        pois = copyOrEmpty(pois);
        transportModes = copyOrEmpty(transportModes);
        routeSegments = copyOrEmpty(routeSegments);
        seasonality = copyOrEmpty(seasonality);
        priceHistory = copyOrEmpty(priceHistory);
        travelApps = copyOrEmpty(travelApps);
    }

    private static <T> List<T> copyOrEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /**
     * One row of {@code destination} (V14).
     *
     * <p>The only node with no {@code sourceRef}, matching the schema: {@code destination} is the
     * one catalogue table without {@code source_id}, because a destination is an identifier for a
     * place rather than a claim about it.
     */
    public record DestinationNode(
            String slug,
            String name,
            String countryCode,
            String timezone,
            BigDecimal latitude,
            BigDecimal longitude,
            CoverageLevel coverageLevel) {
    }

    /**
     * One row of {@code destination_guide} (V14).
     *
     * <p>Three fields rather than one blob because ADR 010 §5 embeds them separately, as field
     * groups {@code OVERVIEW}, {@code FOOD} and {@code PRACTICAL}. A {@code null} section produces
     * no embedding row rather than an empty one.
     */
    public record GuideNode(
            String locale,
            String overview,
            String food,
            String practical,
            String sourceRef) {
    }

    /** One row of {@code destination_area} (V14). {@code slug} is unique within the destination. */
    public record AreaNode(
            String slug,
            String name,
            String description,
            BigDecimal latitude,
            BigDecimal longitude,
            String sourceRef) {
    }

    /**
     * One row of {@code poi} (V15).
     *
     * @param areaSlug nullable — not every POI sits inside a curated area, and requiring one would
     *     invent geography
     * @param openingHours free text, never a structured schedule: real hours are irregular and a
     *     model that cannot express the exception invites a confident wrong answer
     */
    public record PoiNode(
            String slug,
            String name,
            String description,
            PoiCategory category,
            String areaSlug,
            List<String> tags,
            String locale,
            BigDecimal latitude,
            BigDecimal longitude,
            String openingHours,
            PriceBand priceBand,
            String sourceRef) {

        public PoiNode {
            tags = copyOrEmpty(tags);
        }
    }

    /** One row of {@code transport_mode} (V16). */
    public record TransportModeNode(
            String slug,
            String name,
            TransportKind kind,
            String description,
            PriceBand costBand,
            boolean touristFriendly,
            String sourceRef) {
    }

    /**
     * One row of {@code route_segment} (V16), curated for an area pair.
     *
     * @param estimated {@code true} means the leg was inferred from mode heuristics rather than
     *     curated. ADR 010's consequences require the distinction to stay visible: a leg nobody
     *     wrote must announce itself, never be presented as a curated fact
     */
    public record RouteSegmentNode(
            String fromAreaSlug,
            String toAreaSlug,
            String transportModeSlug,
            int durationMinutes,
            boolean estimated,
            String notes,
            String sourceRef) {
    }

    /** One row of {@code seasonality} (V17). All twelve months must be present. */
    public record SeasonalityNode(
            int month,
            WeatherBand weatherBand,
            CrowdBand crowdBand,
            PriceBand priceBand,
            String notes,
            String sourceRef) {
    }

    /**
     * One row of {@code price_history} (V17) — the only place in the TKB holding actual money.
     *
     * @param observedOn the month the figure describes, stored as its first day
     */
    public record PriceObservationNode(
            String category,
            BigDecimal amount,
            String currency,
            LocalDate observedOn,
            String sourceRef) {
    }

    /**
     * One row of {@code travel_app} (V16).
     *
     * <p>Scoped by country rather than by destination, because an app pack is useful across a
     * country and duplicating it per city would make a correction an N-row edit.
     */
    public record TravelAppNode(
            String countryCode,
            String slug,
            String name,
            TravelAppCategory category,
            String description,
            String iosUrl,
            String androidUrl,
            String sourceRef,
            List<AppReplacementNode> replaces) {

        public TravelAppNode {
            // Absent in every seed file written before V21, and optional for most apps afterwards —
            // a transit app replaces nothing. Normalising here rather than null-checking at each use
            // keeps the writer's loop readable and means an omitted key behaves as "none".
            replaces = replaces == null ? List.of() : List.copyOf(replaces);
        }
    }

    /**
     * One row of {@code travel_app_replacement} (V21), nested under the local app that replaces it.
     *
     * <p>Nested rather than a top-level array because that is where a curator can get it right: the
     * statement is "this app replaces that one", and writing it beside the local app's own entry makes
     * the pairing impossible to mis-key. A top-level list would need the local app's slug repeated,
     * which is one more thing to typo into a suppression that silently matches nothing.
     *
     * @param replacedAppKey a lower-case slug for a globally-known app — {@code uber},
     *        {@code whatsapp}. Not country-scoped; the same key means the same product everywhere
     */
    public record AppReplacementNode(
            String replacedAppKey,
            String replacedAppName,
            AppReplacementReason reason,
            String detail) {
    }
}
