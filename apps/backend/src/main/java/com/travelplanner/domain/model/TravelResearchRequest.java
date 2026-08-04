package com.travelplanner.domain.model;

import com.travelplanner.domain.algorithm.ranking.DestinationCandidate;
import com.travelplanner.domain.algorithm.ranking.RankedDestination;
import com.travelplanner.domain.algorithm.ranking.RankingResult;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * What {@link com.travelplanner.domain.port.TravelResearchAgentPort} is asked to narrate.
 *
 * <p>Ranking has already run — {@link #ranking()} carries DSA scores the agent must not overwrite.
 * {@link #candidates()} is the FULL-only KnowledgePort projection used to ground tool calls.
 */
public record TravelResearchRequest(
        TripBriefDetails brief,
        List<DestinationCandidate> candidates,
        RankingResult ranking,
        IntConsumer progress) {

    public TravelResearchRequest {
        Objects.requireNonNull(brief, "brief");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(ranking, "ranking");
        candidates = List.copyOf(candidates);
        progress = progress == null ? pct -> { } : progress;
    }

    public List<RankedDestination> rankedDestinations() {
        return ranking.ranked();
    }
}
