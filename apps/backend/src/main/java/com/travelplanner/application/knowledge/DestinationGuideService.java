package com.travelplanner.application.knowledge;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.exception.DestinationNotFoundException;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.domain.model.DestinationGuide;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.domain.port.KnowledgePort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KB-backed destination guide detail for drawers / chat context (PLAN §4.1, UC-C2-10).
 *
 * <p>Composes guide narrative, areas, top POIs, transport modes, and the locale app pack from
 * {@link KnowledgePort}. Absence of a guide section is typed ({@link Optional}), never invented.
 */
@Service
@RequiresDatabase
public class DestinationGuideService {

    private static final int TOP_POI_LIMIT = 12;

    private final KnowledgePort knowledge;

    public DestinationGuideService(KnowledgePort knowledge) {
        this.knowledge = knowledge;
    }

    /** Full detail for one destination id. */
    @Transactional(readOnly = true)
    public DestinationGuideDetail get(UUID destinationId, String locale) {
        Destination destination = knowledge.findDestinationById(destinationId)
                .orElseThrow(DestinationNotFoundException::new);
        Optional<DestinationGuide> guide = knowledge.findGuide(destinationId, locale);
        List<DestinationArea> areas = knowledge.findAreas(destinationId);
        List<Poi> pois = knowledge.findPois(destinationId, Optional.empty()).stream()
                .limit(TOP_POI_LIMIT)
                .toList();
        List<TransportMode> modes = knowledge.findTransportModes(destinationId);
        List<TravelApp> apps = knowledge.findTravelApps(destination.countryCode());
        return new DestinationGuideDetail(destination, guide, areas, pois, modes, apps);
    }

    /**
     * Assembled guide payload for the HTTP boundary.
     *
     * @param guide absent when that locale was never curated
     */
    public record DestinationGuideDetail(
            Destination destination,
            Optional<DestinationGuide> guide,
            List<DestinationArea> areas,
            List<Poi> pois,
            List<TransportMode> transportModes,
            List<TravelApp> localApps) {
    }
}
