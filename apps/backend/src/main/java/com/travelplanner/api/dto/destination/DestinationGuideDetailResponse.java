package com.travelplanner.api.dto.destination;

import com.travelplanner.application.knowledge.DestinationGuideService.DestinationGuideDetail;
import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.domain.model.DestinationGuide;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import java.util.List;
import java.util.UUID;

/**
 * KB destination detail for drawers / chat context (PLAN §4.1 {@code GET .../guide}).
 *
 * <p>Narrative fields may be null when that locale section was never curated — typed absence,
 * never filler text.
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
        List<SourceRefSummaryResponse> sourceRefs) {

    public static DestinationGuideDetailResponse from(DestinationGuideDetail detail, String locale) {
        DestinationGuide guide = detail.guide().orElse(null);
        List<SourceRefSummaryResponse> refs = guide == null
                ? List.of()
                : List.of(new SourceRefSummaryResponse(
                        guide.provenance().sourceRef(),
                        guide.provenance().sourceUrl(),
                        "overview"));
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
                refs);
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

    public record SourceRefSummaryResponse(String sourceRef, String sourceUrl, String fieldGroup) {
    }
}
