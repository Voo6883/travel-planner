package com.travelplanner.api.dto.destination;

import com.travelplanner.application.knowledge.DestinationGuideService.DestinationGuideDetail;
import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.domain.model.DestinationGuide;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * KB destination detail for drawers / chat context (PLAN §4.1 {@code GET .../guide}).
 *
 * <p>Narrative fields may be null when that locale section was never curated — typed absence,
 * never filler text.
 *
 * <h2>Citations</h2>
 *
 * <p>{@code sourceRefs} covers <em>every</em> part of the payload, not just the narrative. This
 * previously emitted exactly one ref — the guide's own provenance, with the field group hardcoded to
 * {@code "overview"} — which meant the {@code food} and {@code practical} prose was cited to a
 * source that had not been checked for it, and the POIs, areas, transport modes and app pack shown
 * beside them carried no citation at all despite each holding its own provenance in the domain. An
 * uncited fact on a page that displays citations reads as sourced, which is the failure PLAN §4.1.0
 * exists to prevent.
 *
 * @param sampleData ADR 010 §3 — true when any row on this page cites the sample seed. The
 *        per-ref flag says which; this one exists so a client can raise the persistent banner
 *        without walking the list first
 */
public record DestinationGuideDetailResponse(
        UUID destinationId,
        String slug,
        String name,
        String countryCode,
        String locale,
        String overview,
        String food,
        String practical,
        List<AreaSummaryResponse> areas,
        List<PoiSummaryResponse> topPois,
        List<TransportModeResponse> transportModes,
        List<TravelAppSummaryResponse> localAppPack,
        List<SourceRefSummaryResponse> sourceRefs,
        boolean sampleData) {

    public static DestinationGuideDetailResponse from(
            DestinationGuideDetail detail, String locale, Instant now) {
        DestinationGuide guide = detail.guide().orElse(null);
        List<SourceRefSummaryResponse> refs = sourceRefs(detail, guide, now);
        return new DestinationGuideDetailResponse(
                detail.destination().id(),
                detail.destination().slug(),
                detail.destination().name(),
                detail.destination().countryCode(),
                locale,
                guide == null ? null : guide.overview(),
                guide == null ? null : guide.food(),
                guide == null ? null : guide.practical(),
                detail.areas().stream().map(AreaSummaryResponse::from).toList(),
                detail.pois().stream().map(PoiSummaryResponse::from).toList(),
                detail.transportModes().stream().map(TransportModeResponse::from).toList(),
                detail.localApps().stream().map(TravelAppSummaryResponse::from).toList(),
                refs,
                refs.stream().anyMatch(SourceRefSummaryResponse::sampleData));
    }

    /**
     * One ref per {@code (source_ref, field_group)} pair that actually backs something on the page.
     *
     * <p>Deduplicated because a single curated source normally backs every POI in a city, and
     * repeating it twelve times would bury the one row that came from somewhere else. Order is
     * insertion order — narrative first, then the lists as they are rendered — so the citation list
     * reads in the order of the page it annotates.
     *
     * <p>A guide section that was never curated contributes no ref. Citing an absent section would
     * assert that something had been sourced when there is nothing there to source.
     */
    private static List<SourceRefSummaryResponse> sourceRefs(
            DestinationGuideDetail detail, DestinationGuide guide, Instant now) {
        List<SourceRefSummaryResponse> refs = new ArrayList<>();
        Set<List<String>> seen = new LinkedHashSet<>();

        if (guide != null) {
            add(refs, seen, SourceRefSummaryResponse.from(
                    guide.provenance(), "overview", guide.dataClass(), now));
            if (guide.foodIfPresent().isPresent()) {
                add(refs, seen, SourceRefSummaryResponse.from(
                        guide.provenance(), "food", guide.dataClass(), now));
            }
            if (guide.practicalIfPresent().isPresent()) {
                add(refs, seen, SourceRefSummaryResponse.from(
                        guide.provenance(), "practical", guide.dataClass(), now));
            }
        }
        for (DestinationArea area : detail.areas()) {
            add(refs, seen, SourceRefSummaryResponse.from(
                    area.provenance(), "areas", area.dataClass(), now));
        }
        for (Poi poi : detail.pois()) {
            add(refs, seen, SourceRefSummaryResponse.from(
                    poi.provenance(), "pois", poi.dataClass(), now));
        }
        for (TransportMode mode : detail.transportModes()) {
            add(refs, seen, SourceRefSummaryResponse.from(
                    mode.provenance(), "transport", mode.dataClass(), now));
        }
        for (TravelApp app : detail.localApps()) {
            add(refs, seen, SourceRefSummaryResponse.from(
                    app.provenance(), "local_app_pack", app.dataClass(), now));
        }
        return List.copyOf(refs);
    }

    /**
     * Keyed on ref plus field group, not on ref alone: the same source legitimately backs the
     * overview and the POI list, and collapsing those would leave one of the two uncited.
     */
    private static void add(
            List<SourceRefSummaryResponse> refs, Set<List<String>> seen,
            SourceRefSummaryResponse ref) {
        if (seen.add(List.of(ref.sourceRef(), ref.fieldGroup()))) {
            refs.add(ref);
        }
    }

    public record AreaSummaryResponse(
            String slug, String name, String description) {

        static AreaSummaryResponse from(DestinationArea area) {
            return new AreaSummaryResponse(area.slug(), area.name(), area.description());
        }
    }

    public record PoiSummaryResponse(
            UUID poiId, String slug, String name, String category, String description) {

        static PoiSummaryResponse from(Poi poi) {
            return new PoiSummaryResponse(
                    poi.id(), poi.slug(), poi.name(), poi.category().name().toLowerCase(),
                    poi.description());
        }
    }

    public record TransportModeResponse(String mode, String displayName, String description) {

        static TransportModeResponse from(TransportMode mode) {
            return new TransportModeResponse(
                    mode.kind().name(), mode.name(), mode.description());
        }
    }

    public record TravelAppSummaryResponse(
            String slug, String name, String category, String description) {

        static TravelAppSummaryResponse from(TravelApp app) {
            return new TravelAppSummaryResponse(
                    app.slug(), app.name(), app.category().name().toLowerCase(), app.description());
        }
    }

}
