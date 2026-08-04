package com.travelplanner.api.controller;

import com.travelplanner.api.dto.destination.SupportedDestinationsResponse;
import com.travelplanner.application.knowledge.SupportedDestinationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public coverage surface (ADR 010 §4).
 *
 * <p><strong>Unauthenticated on purpose.</strong> This endpoint backs the destination picker and
 * the chat agent's honest "I do not cover that yet". Both of those have to work for a visitor who
 * has not signed in — a picker that answers 401 cannot show anybody what the product does, and a
 * refusal that cannot name an alternative is just a dead end. It publishes a curated catalogue of
 * three cities and nothing about any user, so there is no per-caller data to protect.
 * {@code SecurityConfig.PUBLIC_GET} carries the matching allow-list entry.
 *
 * <p>Routing only (PLAN §4.0: "Controller routes only; Service owns logic"). The coverage rule
 * lives in {@code SupportedDestinationService} and in {@code Destination.requireRankable}, because
 * the refusal path needs the same answer this endpoint returns and two copies of that rule would
 * eventually disagree.
 *
 * <p>Deliberately not {@code @RequiresDatabase}: the route stays mounted in a context with no
 * datasource and answers with an empty list, which is a truthful "nothing is curated" rather than a
 * 404 that reads like a deployment mistake.
 */
@RestController
@RequestMapping("/api/v1/destinations")
public class DestinationController {

    private final SupportedDestinationService destinations;

    public DestinationController(SupportedDestinationService destinations) {
        this.destinations = destinations;
    }

    /** ADR 010 §4 — every destination eligible for C2 ranking. */
    @GetMapping("/supported")
    public SupportedDestinationsResponse supported() {
        return SupportedDestinationsResponse.from(
                destinations.listSupported(), destinations.isSampleData());
    }
}
