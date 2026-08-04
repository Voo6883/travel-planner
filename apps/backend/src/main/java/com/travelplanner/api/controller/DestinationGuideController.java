package com.travelplanner.api.controller;

import com.travelplanner.api.dto.destination.DestinationGuideDetailResponse;
import com.travelplanner.application.knowledge.DestinationGuideService;
import com.travelplanner.config.RequiresDatabase;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Destination guide detail (PLAN §4.1 {@code GET .../destinations/{id}/guide}).
 *
 * <p>Separate from {@link DestinationController} so the public coverage list stays mountable
 * without a datasource, while this richer read requires the knowledge port.
 */
@RestController
@RequestMapping("/api/v1/destinations")
@RequiresDatabase
public class DestinationGuideController {

    private final DestinationGuideService guides;

    public DestinationGuideController(DestinationGuideService guides) {
        this.guides = guides;
    }

    @GetMapping("/{destinationId}/guide")
    public DestinationGuideDetailResponse guide(
            @PathVariable UUID destinationId,
            @RequestParam(defaultValue = "en") String locale) {
        return DestinationGuideDetailResponse.from(guides.get(destinationId, locale), locale);
    }
}
