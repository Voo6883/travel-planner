package com.travelplanner.application.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.research.ResearchTestFakes.InMemoryRecommendations;
import com.travelplanner.application.research.ResearchTestFakes.InMemoryTrips;
import com.travelplanner.application.trip.TripAccess;
import com.travelplanner.domain.algorithm.ranking.ScoreBreakdown;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ResearchNotReadyException;
import com.travelplanner.domain.exception.TripNotFoundException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.RankedRecommendation;
import com.travelplanner.domain.model.ResearchRunResult;
import com.travelplanner.domain.model.TravelerGuide;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.RecommendationSourceRef;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** List/select ownership, status gates, and selection concurrency (UC-C2-03/05/06). */
class ResearchRecommendationServiceTest {

    private static final UserContext OWNER =
            UserContext.of(UUID.randomUUID(), "owner@example.com", Role.USER);
    private static final UserContext STRANGER =
            UserContext.of(UUID.randomUUID(), "stranger@example.com", Role.USER);
    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    private InMemoryTrips trips;
    private InMemoryRecommendations recommendations;
    private ResearchRecommendationService service;

    @BeforeEach
    void setUp() {
        trips = new InMemoryTrips();
        recommendations = new InMemoryRecommendations();
        service = new ResearchRecommendationService(
                new TripAccess(trips), trips, recommendations);
    }

    @Test
    void listReturnsLatestRunWhenResearchReady() {
        UUID tripId = seedTrip(TripStatus.RESEARCH_READY, null);
        RankedRecommendation row = seedRanked(tripId, OWNER.userId());

        ResearchRecommendationsView view = service.list(tripId, OWNER);

        assertThat(view.run().noConfidentResult()).isFalse();
        assertThat(view.run().recommendations()).containsExactly(row);
        assertThat(view.selectedRecommendationId()).isNull();
    }

    @Test
    void listSurfacesTypedEmptyWhenNoConfidentResult() {
        UUID tripId = seedTrip(TripStatus.RESEARCH_READY, null);
        recommendations.seed(ResearchRunResult.noConfident(
                UUID.randomUUID(), tripId, OWNER.userId(), "destination-ranker-v1",
                "travel-research-stub", 1, "stub", List.of(), NOW));

        ResearchRecommendationsView view = service.list(tripId, OWNER);

        assertThat(view.run().noConfidentResult()).isTrue();
        assertThat(view.run().recommendations()).isEmpty();
    }

    @Test
    void listIsRefusedUntilResearchReady() {
        UUID tripId = seedTrip(TripStatus.BRIEF_COMPLETE, null);
        assertThatThrownBy(() -> service.list(tripId, OWNER))
                .isInstanceOf(ResearchNotReadyException.class);
    }

    @Test
    void listIsHiddenFromOtherUsers() {
        UUID tripId = seedTrip(TripStatus.RESEARCH_READY, null);
        seedRanked(tripId, OWNER.userId());
        assertThatThrownBy(() -> service.list(tripId, STRANGER))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void selectPersistsIdAndMovesStatusAtomically() {
        UUID tripId = seedTrip(TripStatus.RESEARCH_READY, null);
        RankedRecommendation row = seedRanked(tripId, OWNER.userId());

        Trip updated = service.select(
                new SelectRecommendationCommand(tripId, row.id()), OWNER);

        assertThat(updated.status()).isEqualTo(TripStatus.DESTINATION_SELECTED);
        assertThat(updated.selectedRecommendation()).contains(row.id());
        assertThat(trips.stored(tripId).status()).isEqualTo(TripStatus.DESTINATION_SELECTED);
    }

    @Test
    void selectRefusesWrongStatusAndForeignRecommendation() {
        UUID tripId = seedTrip(TripStatus.BRIEF_COMPLETE, null);
        RankedRecommendation row = seedRanked(tripId, OWNER.userId());
        assertThatThrownBy(() -> service.select(
                new SelectRecommendationCommand(tripId, row.id()), OWNER))
                .isInstanceOf(ValidationFailedException.class);

        UUID readyId = seedTrip(TripStatus.RESEARCH_READY, null);
        seedRanked(readyId, OWNER.userId());
        assertThatThrownBy(() -> service.select(
                new SelectRecommendationCommand(readyId, UUID.randomUUID()), OWNER))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void selectIsIdempotentAgainstAlreadySelectedStatus() {
        RankedRecommendation row = seedRanked(
                seedTrip(TripStatus.DESTINATION_SELECTED, UUID.randomUUID()), OWNER.userId());
        UUID tripId = row.tripId();
        trips.seed(new Trip(tripId, OWNER.userId(), "Kyoto", TripStatus.DESTINATION_SELECTED,
                row.id(), 3, NOW, NOW));

        assertThatThrownBy(() -> service.select(
                new SelectRecommendationCommand(tripId, row.id()), OWNER))
                .isInstanceOf(ValidationFailedException.class);
    }

    private UUID seedTrip(TripStatus status, UUID selectedId) {
        Trip trip = new Trip(UUID.randomUUID(), OWNER.userId(), "Kyoto", status, selectedId, 3,
                NOW, NOW);
        trips.seed(trip);
        return trip.id();
    }

    private RankedRecommendation seedRanked(UUID tripId, UUID userId) {
        UUID runId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        ScoreBreakdown breakdown = new ScoreBreakdown(
                0.8, 0.7, 0.6, 0.5, 0.9, 0.85, 0.72, Money.of("1200", "USD"));
        TravelerGuide guide = new TravelerGuide(
                "Overview", "Why now", List.of("Gion"), "Food", List.of("Highlight"),
                "Mobility", "Practical", List.of(),
                List.of(RecommendationSourceRef.of("wikivoyage:kyoto", "overview")));
        RankedRecommendation row = new RankedRecommendation(
                UUID.randomUUID(), tripId, userId, runId, destinationId, "kyoto-jp", "JP", 1,
                breakdown, "Strong fit", guide, List.of("crowds"), "spring",
                List.of(RecommendationSourceRef.of("wikivoyage:kyoto", "overview")),
                "destination-ranker-v1", NOW);
        recommendations.seed(ResearchRunResult.ranked(
                runId, tripId, userId, "destination-ranker-v1", "travel-research-stub", 1, "stub",
                List.of(), List.of(row), NOW));
        return row;
    }
}
