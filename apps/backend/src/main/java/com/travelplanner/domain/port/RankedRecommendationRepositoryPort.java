package com.travelplanner.domain.port;

import com.travelplanner.domain.model.RankedRecommendation;
import com.travelplanner.domain.model.ResearchRunResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for C2 research outcomes (task 25). Task 26 reads these for the list/select API.
 *
 * <p>Scoped by {@code userId} on every read that surfaces to a caller, matching PLAN §4.0.2-L.
 */
public interface RankedRecommendationRepositoryPort {

    /** Inserts the run result and any recommendation rows in one caller's transaction. */
    ResearchRunResult save(ResearchRunResult result);

    Optional<ResearchRunResult> findRunByResearchRunId(UUID researchRunId, UUID userId);

    /** Newest run for the trip, when any exists. */
    Optional<ResearchRunResult> findLatestRunByTripId(UUID tripId, UUID userId);

    List<RankedRecommendation> findByResearchRunId(UUID researchRunId, UUID userId);
}
