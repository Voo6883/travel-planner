package com.travelplanner.application.research;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.algorithm.ranking.DestinationCandidate;
import com.travelplanner.domain.algorithm.ranking.DestinationRanker;
import com.travelplanner.domain.algorithm.ranking.RankedDestination;
import com.travelplanner.domain.algorithm.ranking.RankingInput;
import com.travelplanner.domain.algorithm.ranking.RankingResult;
import com.travelplanner.domain.algorithm.ranking.ScoringWeights;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.ClarificationNeeded;
import com.travelplanner.domain.model.DestinationNarrative;
import com.travelplanner.domain.model.RankedRecommendation;
import com.travelplanner.domain.model.ResearchRunResult;
import com.travelplanner.domain.model.TravelResearchNarratives;
import com.travelplanner.domain.model.TravelResearchRequest;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.port.TravelResearchAgentPort;
import com.travelplanner.domain.port.TripBriefRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Real {@link ResearchJobHandler}: revalidates brief/status, ranks FULL candidates, invokes
 * {@link TravelResearchAgentPort}, and stages a durable outcome for the completion hook (task 25).
 *
 * <p>Registered as a scanned {@code @Service} so it wins over the
 * {@code @ConditionalOnMissingBean} no-op in {@code ResearchExecutionConfig}. No LLM/HTTP runs
 * inside a transaction — ranking and the agent execute here with no DB write held open; persistence
 * is the completion hook's job.
 */
@Service
@RequiresDatabase
public class TravelResearchJobHandler implements ResearchJobHandler {

    private static final Logger log = LoggerFactory.getLogger(TravelResearchJobHandler.class);
    private static final int TOP_K = 5;

    private final TripRepositoryPort trips;
    private final TripBriefRepositoryPort briefs;
    private final DestinationCandidateBuilder candidates;
    private final TravelResearchAgentPort agent;
    private final PendingResearchOutcomeStore pending;
    private final DestinationRanker ranker;
    private final Clock clock;

    public TravelResearchJobHandler(
            TripRepositoryPort trips,
            TripBriefRepositoryPort briefs,
            DestinationCandidateBuilder candidates,
            TravelResearchAgentPort agent,
            PendingResearchOutcomeStore pending,
            @org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock) {
        this.trips = trips;
        this.briefs = briefs;
        this.candidates = candidates;
        this.agent = agent;
        this.pending = pending;
        this.ranker = new DestinationRanker();
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public void execute(ResearchJobContext context) {
        Snapshot snapshot = loadSnapshot(context);
        context.reportProgress(10);

        List<DestinationCandidate> built = candidates.build(snapshot.brief().details());
        context.reportProgress(25);

        RankingResult ranking = ranker.topK(new RankingInput(
                built, snapshot.brief().details(), ScoringWeights.defaults(), TOP_K, clock.instant()));
        context.reportProgress(40);

        TravelResearchNarratives narratives = agent.research(new TravelResearchRequest(
                snapshot.brief().details(), built, ranking, context::reportProgress));

        ResearchRunResult outcome = assemble(context, snapshot, ranking, narratives);
        pending.put(context.jobId(), outcome);
        context.reportProgress(100);
        log.info("research_agent_ready job={} run={} no_confident={} ranked={}",
                context.jobId(), context.researchRunId(), outcome.noConfidentResult(),
                outcome.recommendations().size());
    }

    private Snapshot loadSnapshot(ResearchJobContext context) {
        Trip trip = trips.findByIdAndUserId(context.tripId(), context.userId())
                .orElseThrow(() -> ValidationFailedException.field("trip_id", "trip not found"));
        if (trip.status() != TripStatus.RESEARCH_RUNNING) {
            throw ValidationFailedException.field("status",
                    "research handler expected RESEARCH_RUNNING, was " + trip.status());
        }
        TripBrief brief = briefs.findByTripId(context.tripId())
                .orElseThrow(() -> ValidationFailedException.field("brief", "trip brief missing"));
        TripBriefDetails details = brief.details();
        if (!ClarificationNeeded.forDetails(details).isSatisfied()) {
            throw ValidationFailedException.field("brief",
                    "trip brief is no longer complete — refusing research");
        }
        return new Snapshot(trip, brief);
    }

    private ResearchRunResult assemble(
            ResearchJobContext context,
            Snapshot snapshot,
            RankingResult ranking,
            TravelResearchNarratives narratives) {
        Instant now = clock.instant();
        if (ranking.noConfidentResult()) {
            return ResearchRunResult.noConfident(
                    context.researchRunId(),
                    context.tripId(),
                    context.userId(),
                    ranking.algorithmVersion(),
                    narratives.promptTemplateId(),
                    narratives.promptVersion(),
                    narratives.modelName(),
                    ranking.excluded(),
                    now);
        }
        Map<UUID, DestinationNarrative> byId = narratives.byDestinationId();
        List<RankedRecommendation> rows = new ArrayList<>();
        for (RankedDestination ranked : ranking.ranked()) {
            DestinationNarrative narrative = byId.get(ranked.destinationId());
            if (narrative == null) {
                throw ValidationFailedException.field("narratives",
                        "missing narrative for ranked destination " + ranked.slug());
            }
            rows.add(new RankedRecommendation(
                    UUID.randomUUID(),
                    context.tripId(),
                    context.userId(),
                    context.researchRunId(),
                    ranked.destinationId(),
                    ranked.slug(),
                    ranked.countryCode(),
                    ranked.rank(),
                    ranked.breakdown(),
                    narrative.rationale(),
                    narrative.travelerGuide(),
                    narrative.risks(),
                    narrative.bestWindow(),
                    narrative.sourceRefs(),
                    ranking.algorithmVersion(),
                    now));
        }
        return ResearchRunResult.ranked(
                context.researchRunId(),
                context.tripId(),
                context.userId(),
                ranking.algorithmVersion(),
                narratives.promptTemplateId(),
                narratives.promptVersion(),
                narratives.modelName(),
                ranking.excluded(),
                rows,
                now);
    }

    private record Snapshot(Trip trip, TripBrief brief) {
    }
}
