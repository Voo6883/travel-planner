package com.travelplanner.application.tripchat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.application.knowledge.DestinationGuideService;
import com.travelplanner.application.research.ResearchJobService;
import com.travelplanner.application.research.ResearchJobView;
import com.travelplanner.application.research.ResearchRecommendationService;
import com.travelplanner.application.research.ResearchRecommendationsView;
import com.travelplanner.application.research.SelectRecommendationCommand;
import com.travelplanner.application.research.StartResearchCommand;
import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.application.trip.TripAccess;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.Message;
import com.travelplanner.domain.model.RankedRecommendation;
import com.travelplanner.domain.model.ResearchJob;
import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.port.ConversationRepositoryPort;
import com.travelplanner.domain.port.KnowledgePort;
import com.travelplanner.domain.port.ResearchJobRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Status-gated C2 research tools for trip chat (task 27, UC-C5-03/04/05, UC-C2-12).
 *
 * <p>Mutations delegate to {@link ResearchJobService} / {@link ResearchRecommendationService}.
 * Read tools return only persisted job / recommendation / KB data — never invented percentages or
 * travel facts. No LLM or HTTP inside these transactions.
 */
@Service
@RequiresDatabase
public class TripChatResearchToolService {

    private final TripAccess access;
    private final ResearchJobService jobs;
    private final ResearchJobRepositoryPort jobRows;
    private final ResearchRecommendationService recommendations;
    private final DestinationGuideService guides;
    private final KnowledgePort knowledge;
    private final ConversationRepositoryPort conversations;
    private final ObjectMapper objectMapper;

    public TripChatResearchToolService(
            TripAccess access,
            ResearchJobService jobs,
            ResearchJobRepositoryPort jobRows,
            ResearchRecommendationService recommendations,
            DestinationGuideService guides,
            KnowledgePort knowledge,
            ConversationRepositoryPort conversations,
            ObjectMapper objectMapper) {
        this.access = access;
        this.jobs = jobs;
        this.jobRows = jobRows;
        this.recommendations = recommendations;
        this.guides = guides;
        this.knowledge = knowledge;
        this.conversations = conversations;
        this.objectMapper = objectMapper;
    }

    @TransactionalWrite
    public Result startResearch(TripChatToolService.Context context, StartResearchArgs args) {
        Trip trip = access.requireEditable(context.tripId(), context.user());
        requireAllowed(trip.status(), context.toolName());
        ResearchJobView job = jobs.start(new StartResearchCommand(context.tripId()), context.user());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trip_id", context.tripId().toString());
        body.put("status", TripStatus.RESEARCH_QUEUED.name());
        body.put("job_id", job.jobId().toString());
        body.put("job_status", job.status().name());
        String payload = serialise(body);
        appendToolMessages(context, args.inputJson(), payload);
        return new Result(payload, EventKind.RESEARCH_STARTED, job.jobId());
    }

    @Transactional(readOnly = true)
    public Result researchStatus(TripChatToolService.Context context, EmptyToolArgs args) {
        Trip trip = access.requireOwned(context.tripId(), context.user());
        requireAllowed(trip.status(), context.toolName());
        ResearchJob job = jobRows.findLatestByTripId(trip.id()).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trip_id", trip.id().toString());
        body.put("trip_status", trip.status().name());
        if (job == null) {
            body.put("job", null);
            body.put("note", "no research job has been started for this trip");
        } else {
            body.put("job", jobSummary(job));
            body.put("note", statusNote(job));
        }
        return new Result(serialise(body), EventKind.NONE, null);
    }

    @Transactional(readOnly = true)
    public Result recommendationsSummary(TripChatToolService.Context context, EmptyToolArgs args) {
        Trip trip = access.requireOwned(context.tripId(), context.user());
        requireAllowed(trip.status(), context.toolName());
        ResearchRecommendationsView view = recommendations.list(context.tripId(), context.user());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trip_id", trip.id().toString());
        body.put("research_run_id", view.run().researchRunId().toString());
        body.put("no_confident_result", view.run().noConfidentResult());
        body.put("algorithm_version", view.run().algorithmVersion());
        body.put("selected_recommendation_id", view.selectedRecommendationId() == null
                ? null : view.selectedRecommendationId().toString());
        body.put("recommendations", summarise(view.run().recommendations()));
        return new Result(serialise(body), EventKind.NONE, null);
    }

    @TransactionalWrite
    public Result selectRecommendation(
            TripChatToolService.Context context, SelectRecommendationArgs args) {
        Trip trip = access.requireEditable(context.tripId(), context.user());
        requireAllowed(trip.status(), context.toolName());
        UUID recommendationId = resolveSelection(args, context);
        Trip updated = recommendations.select(
                new SelectRecommendationCommand(context.tripId(), recommendationId), context.user());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trip_id", updated.id().toString());
        body.put("status", updated.status().name());
        body.put("selected_recommendation_id", recommendationId.toString());
        body.put("confirmation", args.confirmation().name().toLowerCase());
        String payload = serialise(body);
        appendToolMessages(context, args.inputJson(), payload);
        return new Result(payload, EventKind.DESTINATION_SELECTED, null);
    }

    @Transactional(readOnly = true)
    public Result destinationGuide(
            TripChatToolService.Context context, GetDestinationGuideArgs args) {
        Trip trip = access.requireOwned(context.tripId(), context.user());
        requireAllowed(trip.status(), context.toolName());
        var detail = guides.get(args.destinationId(), args.locale());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("destination_id", detail.destination().id().toString());
        body.put("slug", detail.destination().slug());
        body.put("country_code", detail.destination().countryCode());
        body.put("locale", args.locale());
        body.put("guide_present", detail.guide().isPresent());
        detail.guide().ifPresent(guide -> {
            body.put("overview", guide.overview());
            body.put("source_ref", guide.provenance().sourceRef());
        });
        body.put("area_count", detail.areas().size());
        body.put("poi_count", detail.pois().size());
        body.put("transport_mode_count", detail.transportModes().size());
        body.put("app_count", detail.localApps().size());
        return new Result(serialise(body), EventKind.NONE, null);
    }

    @Transactional(readOnly = true)
    public Result travelApps(TripChatToolService.Context context, GetTravelAppsArgs args) {
        Trip trip = access.requireOwned(context.tripId(), context.user());
        requireAllowed(trip.status(), context.toolName());
        List<TravelApp> apps = knowledge.findTravelApps(args.countryCode());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TravelApp app : apps) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slug", app.slug());
            row.put("name", app.name());
            row.put("category", app.category().name());
            row.put("source_ref", app.provenance().sourceRef());
            rows.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("country_code", args.countryCode());
        body.put("apps", rows);
        return new Result(serialise(body), EventKind.NONE, null);
    }

    private UUID resolveSelection(SelectRecommendationArgs args, TripChatToolService.Context ctx) {
        if (args.confirmation() == SelectRecommendationArgs.Confirmation.EXPLICIT) {
            return args.recommendationId();
        }
        ResearchRecommendationsView view = recommendations.list(ctx.tripId(), ctx.user());
        if (view.run().noConfidentResult() || view.run().recommendations().isEmpty()) {
            throw ValidationFailedException.field("recommendation_id",
                    "no confident recommendation to pick");
        }
        return view.run().recommendations().getFirst().id();
    }

    private static void requireAllowed(TripStatus status, String toolName) {
        if (!TripChatTools.isAllowed(status, toolName)) {
            throw ValidationFailedException.field("status",
                    toolName + " is not available while the trip is " + status);
        }
    }

    private static Map<String, Object> jobSummary(ResearchJob job) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("job_id", job.id().toString());
        row.put("status", job.status().name());
        row.put("progress_pct", job.progressPct());
        row.put("error_code", job.errorCode());
        return row;
    }

    private static String statusNote(ResearchJob job) {
        return switch (job.status()) {
            case QUEUED -> "Research is queued; progress_pct is advisory only.";
            case RUNNING -> "Research is running; use only the persisted progress_pct.";
            case COMPLETED -> "Research completed; recommendations are available when trip is ready.";
            case FAILED -> "Research failed with error_code=" + job.errorCode()
                    + "; the trip returned to BRIEF_COMPLETE for re-run.";
        };
    }

    private static List<Map<String, Object>> summarise(List<RankedRecommendation> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RankedRecommendation row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("recommendation_id", row.id().toString());
            item.put("rank", row.rank());
            item.put("destination_id", row.destinationId().toString());
            item.put("destination_slug", row.destinationSlug());
            item.put("country_code", row.countryCode());
            item.put("fit_score", row.fitScore());
            item.put("rationale", row.rationale());
            item.put("risks", row.risks());
            item.put("best_window", row.bestWindow());
            item.put("source_refs", row.sourceRefs().stream()
                    .map(ref -> ref.sourceRef()).toList());
            out.add(item);
        }
        return out;
    }

    private void appendToolMessages(TripChatToolService.Context context, String input, String payload) {
        Instant now = Instant.now();
        UUID conversationId = context.conversationId();
        UUID userId = context.user().userId();
        long callSeq = conversations.allocateSequence(conversationId, userId);
        conversations.appendMessage(Message.toolCall(conversationId, callSeq, context.toolCallId(),
                context.toolName(), input, now));
        long resultSeq = conversations.allocateSequence(conversationId, userId);
        conversations.appendMessage(Message.toolResult(conversationId, resultSeq, context.toolCallId(),
                payload, now));
        conversations.findConversationByIdAndUserId(conversationId, userId)
                .ifPresent(c -> conversations.saveConversation(c.touchLastMessageAt(now)));
    }

    private String serialise(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not serialise a research chat tool result", failure);
        }
    }

    public enum EventKind {
        NONE,
        RESEARCH_STARTED,
        DESTINATION_SELECTED
    }

    public record Result(String payloadJson, EventKind eventKind, UUID jobId) {
    }
}
