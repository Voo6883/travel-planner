package com.travelplanner.application.tripchat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.application.ai.LlmStreamPort;
import com.travelplanner.application.chat.ChatTurn;
import com.travelplanner.application.trip.TripBriefService;
import com.travelplanner.application.trip.TripBriefView;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.ai.StopReason;
import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.exception.DomainException;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Trip-thread turns with status-gated intake and research tools (tasks 22 and 27).
 *
 * <p>Tool loops stay bounded by {@link #MAX_ROUNDS}. Mutations go through
 * {@link TripChatToolService} / {@link TripChatResearchToolService}; domain events follow committed
 * writes only (ADR 007).
 */
@Service
@RequiresDatabase
public class TripChatOrchestrator {

    static final int MAX_ROUNDS = 4;

    private static final Logger log = LoggerFactory.getLogger(TripChatOrchestrator.class);
    private static final String FEATURE = "chat";
    private static final String BRIEF_UPDATED = "brief_updated";
    private static final String RESEARCH_STARTED = "research_started";
    private static final String DESTINATION_SELECTED = "destination_selected";
    private static final String TRIP_ID_KEY = "trip_id";
    private static final String JOB_ID_KEY = "job_id";
    private static final LlmOptions OPTIONS = LlmOptions.forFeature(FEATURE);

    private final LlmStreamPort llm;
    private final TripChatToolService toolService;
    private final TripChatResearchToolService researchTools;
    private final TripBriefService briefs;
    private final ObjectMapper objectMapper;

    public TripChatOrchestrator(
            LlmStreamPort llm,
            TripChatToolService toolService,
            TripChatResearchToolService researchTools,
            TripBriefService briefs,
            ObjectMapper objectMapper) {
        this.llm = llm;
        this.toolService = toolService;
        this.researchTools = researchTools;
        this.briefs = briefs;
        this.objectMapper = objectMapper;
    }

    public Flux<LlmEvent> stream(ChatTurn turn) {
        if (turn.target().isPlanner()) {
            return llm.stream(turn.prompt(), OPTIONS);
        }
        return Flux.defer(() -> {
            UUID tripId = turn.target().tripIfPresent().orElseThrow();
            TripBriefView view = briefs.get(tripId, turn.user());
            Prompt prompt = TripChatPrompt.enrich(turn, view);
            List<ToolSpec> tools = TripChatTools.specsFor(view.status());
            return round(new Round(turn, tripId, prompt, tools, 1));
        });
    }

    private Flux<LlmEvent> round(Round round) {
        ToolBuffer buffer = new ToolBuffer(round);
        return llm.stream(round.prompt(), round.tools(), OPTIONS)
                .concatMap(event -> Flux.fromIterable(buffer.handle(event)))
                .concatWith(Flux.defer(buffer::continuation));
    }

    private Outcome execute(ToolCall call, Round round) {
        try {
            Executed executed = run(call, round);
            List<LlmEvent> events = new ArrayList<>();
            events.add(new LlmEvent.ToolResult(call.id(), executed.payloadJson()));
            if (executed.domainEvent() != null) {
                events.add(executed.domainEvent());
            }
            return Outcome.ok(List.copyOf(events), executed.payloadJson());
        } catch (DomainException failure) {
            log.warn("trip_tool_rejected tool={} code={} detail={}", call.name(), failure.code(),
                    failure.details());
            return Outcome.failed(
                    new LlmEvent.StreamError(failure.code(), failure.getMessage(), failure.details()));
        }
    }

    private Executed run(ToolCall call, Round round) {
        TripChatToolService.Context context = new TripChatToolService.Context(
                round.turn().conversationId(), round.tripId(), call.id(), call.name(),
                round.turn().user());
        if (TripChatTools.UPDATE_TRIP_BRIEF.equals(call.name())) {
            TripChatToolService.Result result = toolService.applyUpdate(context,
                    UpdateTripBriefArgs.parse(call.name(), call.inputJson(), objectMapper));
            return new Executed(result.payloadJson(), briefUpdated(round.tripId()));
        }
        if (TripChatTools.ANSWER_CLARIFICATION.equals(call.name())) {
            TripChatToolService.Result result = toolService.applyClarification(context,
                    AnswerClarificationArgs.parse(call.name(), call.inputJson(), objectMapper));
            return new Executed(result.payloadJson(), briefUpdated(round.tripId()));
        }
        return runResearch(call, context, round.tripId());
    }

    private Executed runResearch(ToolCall call, TripChatToolService.Context context, UUID tripId) {
        TripChatResearchToolService.Result result = switch (call.name()) {
            case TripChatTools.START_RESEARCH -> researchTools.startResearch(context,
                    StartResearchArgs.parse(call.name(), call.inputJson(), objectMapper));
            case TripChatTools.GET_RESEARCH_STATUS -> researchTools.researchStatus(context,
                    EmptyToolArgs.parse(call.name(), call.inputJson(), objectMapper));
            case TripChatTools.GET_RECOMMENDATIONS_SUMMARY -> researchTools.recommendationsSummary(
                    context, EmptyToolArgs.parse(call.name(), call.inputJson(), objectMapper));
            case TripChatTools.SELECT_RECOMMENDATION -> researchTools.selectRecommendation(context,
                    SelectRecommendationArgs.parse(call.name(), call.inputJson(), objectMapper));
            case TripChatTools.GET_DESTINATION_GUIDE -> researchTools.destinationGuide(context,
                    GetDestinationGuideArgs.parse(call.name(), call.inputJson(), objectMapper));
            case TripChatTools.GET_TRAVEL_APPS -> researchTools.travelApps(context,
                    GetTravelAppsArgs.parse(call.name(), call.inputJson(), objectMapper));
            default -> throw ValidationFailedException.field(
                    "tool_name", "unknown trip tool: " + call.name());
        };
        return new Executed(result.payloadJson(), domainEventFor(result, tripId));
    }

    private static LlmEvent.DomainEvent briefUpdated(UUID tripId) {
        return new LlmEvent.DomainEvent(BRIEF_UPDATED, Map.of(TRIP_ID_KEY, tripId.toString()));
    }

    private static LlmEvent.DomainEvent domainEventFor(
            TripChatResearchToolService.Result result, UUID tripId) {
        return switch (result.eventKind()) {
            case NONE -> null;
            case RESEARCH_STARTED -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put(TRIP_ID_KEY, tripId.toString());
                payload.put(JOB_ID_KEY, result.jobId().toString());
                yield new LlmEvent.DomainEvent(RESEARCH_STARTED, payload);
            }
            case DESTINATION_SELECTED -> new LlmEvent.DomainEvent(
                    DESTINATION_SELECTED, Map.of(TRIP_ID_KEY, tripId.toString()));
        };
    }

    private static Prompt continuation(Round round, String assistantText,
            List<PromptMessage> toolResults) {
        List<PromptMessage> messages = new ArrayList<>(round.prompt().messages());
        messages.add(PromptMessage.assistant(assistantText.isBlank() ? "(calling tools)" : assistantText));
        messages.addAll(toolResults);
        return Prompt.adHoc(List.copyOf(messages));
    }

    private final class ToolBuffer {

        private final Round round;
        private final Map<String, ToolCall> calls = new HashMap<>();
        private final List<PromptMessage> toolResults = new ArrayList<>();
        private final StringBuilder assistantText = new StringBuilder();
        private StopReason stopReason;
        private boolean toolExecuted;
        private boolean failed;

        private ToolBuffer(Round round) {
            this.round = round;
        }

        private List<LlmEvent> handle(LlmEvent event) {
            return switch (event) {
                case LlmEvent.TextDelta delta -> text(delta);
                case LlmEvent.ToolUseStart start -> start(start);
                case LlmEvent.ToolInputDelta delta -> append(delta);
                case LlmEvent.ToolUseEnd end -> end(end);
                case LlmEvent.Done done -> done(done);
                case LlmEvent.StreamError error -> providerError(error);
                default -> List.of(event);
            };
        }

        private List<LlmEvent> text(LlmEvent.TextDelta delta) {
            assistantText.append(delta.text());
            return List.of(delta);
        }

        private List<LlmEvent> start(LlmEvent.ToolUseStart start) {
            calls.put(start.toolCallId(), new ToolCall(start.toolCallId(), start.name()));
            return List.of(start);
        }

        private List<LlmEvent> append(LlmEvent.ToolInputDelta delta) {
            calls.computeIfAbsent(delta.toolCallId(), id -> new ToolCall(id, ""))
                    .append(delta.jsonChunk());
            return List.of(delta);
        }

        private List<LlmEvent> end(LlmEvent.ToolUseEnd end) {
            ToolCall call = calls.computeIfAbsent(end.toolCallId(), id -> new ToolCall(id, ""));
            Outcome outcome = execute(call, round);
            List<LlmEvent> events = new ArrayList<>();
            events.add(end);
            events.addAll(outcome.events());
            if (outcome.failed()) {
                failed = true;
            } else {
                toolExecuted = true;
                toolResults.add(PromptMessage.toolResult(call.id(), outcome.toolResultPayload()));
            }
            return List.copyOf(events);
        }

        private List<LlmEvent> providerError(LlmEvent.StreamError error) {
            failed = true;
            return List.of(error);
        }

        private List<LlmEvent> done(LlmEvent.Done done) {
            stopReason = done.stopReason();
            return List.of();
        }

        private Flux<LlmEvent> continuation() {
            if (failed) {
                return Flux.empty();
            }
            if (shouldLoop()) {
                Prompt next = TripChatOrchestrator.continuation(round, assistantText.toString(),
                        toolResults);
                return round(round.next(next));
            }
            return Flux.just(new LlmEvent.Done(stopReason == null ? StopReason.END_TURN : stopReason));
        }

        private boolean shouldLoop() {
            return stopReason == StopReason.TOOL_USE && toolExecuted && round.number() < MAX_ROUNDS;
        }
    }

    private record Round(ChatTurn turn, UUID tripId, Prompt prompt, List<ToolSpec> tools, int number) {

        private Round next(Prompt nextPrompt) {
            return new Round(turn, tripId, nextPrompt, tools, number + 1);
        }
    }

    private record Outcome(List<LlmEvent> events, String toolResultPayload, boolean failed) {

        private static Outcome ok(List<LlmEvent> events, String toolResultPayload) {
            return new Outcome(events, toolResultPayload, false);
        }

        private static Outcome failed(LlmEvent.StreamError error) {
            return new Outcome(List.of(error), null, true);
        }
    }

    private record Executed(String payloadJson, LlmEvent.DomainEvent domainEvent) {
    }

    private static final class ToolCall {

        private final String id;
        private final String name;
        private final StringBuilder input = new StringBuilder();

        private ToolCall(String id, String name) {
            this.id = id;
            this.name = name;
        }

        private String id() {
            return id;
        }

        private String name() {
            return name;
        }

        private String inputJson() {
            return input.toString();
        }

        private void append(String chunk) {
            input.append(chunk);
        }
    }
}
