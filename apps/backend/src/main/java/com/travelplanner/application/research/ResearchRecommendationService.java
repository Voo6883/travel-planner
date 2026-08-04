package com.travelplanner.application.research;

import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.application.trip.TripAccess;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ResearchNotReadyException;
import com.travelplanner.domain.exception.TripNotFoundException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.RankedRecommendation;
import com.travelplanner.domain.model.ResearchRunResult;
import com.travelplanner.domain.model.ResearchStatusTransition;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.port.RankedRecommendationRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * List and select C2 research outcomes (UC-C2-03/05/06, tasks/26).
 *
 * <p>Reads the durable V25 rows task 25 wrote. Never recomputes fit scores. Selection moves
 * {@code RESEARCH_READY → DESTINATION_SELECTED} and persists {@code selected_recommendation_id}
 * in one transaction.
 */
@Service
@RequiresDatabase
public class ResearchRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(ResearchRecommendationService.class);

    private final TripAccess access;
    private final TripRepositoryPort trips;
    private final RankedRecommendationRepositoryPort recommendations;

    public ResearchRecommendationService(
            TripAccess access,
            TripRepositoryPort trips,
            RankedRecommendationRepositoryPort recommendations) {
        this.access = access;
        this.trips = trips;
        this.recommendations = recommendations;
    }

    /**
     * Latest run for the trip when status allows listing (UC-C2-03/05).
     *
     * @throws ResearchNotReadyException when the trip is not ready or has no durable outcome
     * @throws TripNotFoundException when the trip is not the caller's
     */
    @Transactional(readOnly = true)
    public ResearchRecommendationsView list(UUID tripId, UserContext user) {
        Trip trip = access.requireOwned(tripId, user);
        requireListable(trip);
        ResearchRunResult run = recommendations.findLatestRunByTripId(trip.id(), user.userId())
                .orElseThrow(ResearchNotReadyException::new);
        return new ResearchRecommendationsView(run, trip.selectedRecommendationId());
    }

    /**
     * Confirms one recommendation (UC-C2-06). Atomic status + selection write.
     *
     * @throws ResearchNotReadyException when there is no durable run to select from
     * @throws ValidationFailedException when status is not {@code RESEARCH_READY} or the id is
     *         not part of the latest run for this trip
     */
    @TransactionalWrite
    public Trip select(SelectRecommendationCommand command, UserContext user) {
        Trip trip = access.requireEditable(command.tripId(), user);
        TripStatus target = ResearchStatusTransition.requireSelect(trip.status());
        ResearchRunResult run = recommendations.findLatestRunByTripId(trip.id(), user.userId())
                .orElseThrow(ResearchNotReadyException::new);
        RankedRecommendation chosen = requireInLatestRun(command.recommendationId(), run, user);

        Instant now = Instant.now();
        Trip updated = trips.save(
                trip.withSelectedRecommendation(chosen.id(), target, now));
        log.info("destination_selected trip={} recommendation={} user={}",
                trip.id(), chosen.id(), user.userId());
        return updated;
    }

    private static void requireListable(Trip trip) {
        if (!ResearchStatusTransition.canListRecommendations(trip.status())) {
            throw new ResearchNotReadyException();
        }
        if (trip.status() == TripStatus.DESTINATION_SELECTED
                && trip.selectedRecommendation().isEmpty()) {
            throw new ResearchNotReadyException();
        }
    }

    private RankedRecommendation requireInLatestRun(
            UUID recommendationId, ResearchRunResult run, UserContext user) {
        RankedRecommendation found = recommendations
                .findByIdAndUserId(recommendationId, user.userId())
                .orElseThrow(() -> ValidationFailedException.field(
                        "recommendation_id", "recommendation was not found for this trip"));
        if (!found.tripId().equals(run.tripId())
                || !found.researchRunId().equals(run.researchRunId())) {
            throw ValidationFailedException.field(
                    "recommendation_id", "recommendation is not from the latest research run");
        }
        if (run.noConfidentResult()) {
            throw ValidationFailedException.field(
                    "recommendation_id", "this research run has no confident recommendation");
        }
        return found;
    }
}
