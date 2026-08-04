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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Runs a trip-thread turn: enriches the prompt with the trip's own state, offers the status-gated
 * intake tools, and executes the one the model chooses (task 22, UC-C5-09).
 *
 * <p><strong>The tool loop is why this is not {@code PlannerChatOrchestrator}.</strong> Creating a
 * trip is a single terminal act — the planner streams once, runs {@code create_trip}, and is done.
 * Editing a brief is a conversation: the model saves a field, reads back what the server stored,
 * and may then save another. So a provider turn that stops on {@link StopReason#TOOL_USE} after a
 * tool committed is <em>not</em> the end of the turn — it is a cue to feed the tool result back and
 * stream again. {@link #MAX_ROUNDS} caps that at a handful of rounds so a model that never settles
 * cannot bill an unbounded number of calls, and the intermediate {@code Done(TOOL_USE)} frames are
 * swallowed so the client sees one continuous turn ending in one terminal frame.
 *
 * <p>No mutation happens here. {@link TripChatToolService} owns the transaction, the status gate,
 * and the optimistic lock; this class parses arguments, calls it, and turns the outcome into
 * stream events — a committed edit becomes a {@link LlmEvent.ToolResult} plus a
 * {@link LlmEvent.DomainEvent} {@code brief_updated}, and any {@link DomainException} becomes a
 * terminal {@link LlmEvent.StreamError} carrying the registered code (PLAN: "no LLM/HTTP inside
 * {@code @Transactional}" — the model call is out here, the write is in the service).
 */
@Service
@RequiresDatabase
public class TripChatOrchestrator {

    /** One initial round plus at most three tool-driven follow-ups. */
    static final int MAX_ROUNDS = 4;

    private static final Logger log = LoggerFactory.getLogger(TripChatOrchestrator.class);
    private static final String FEATURE = "chat";
    private static final String BRIEF_UPDATED = "brief_updated";
    private static final String TRIP_ID_KEY = "trip_id";
    private static final LlmOptions OPTIONS = LlmOptions.forFeature(FEATURE);

    private final LlmStreamPort llm;
    private final TripChatToolService toolService;
    private final TripBriefService briefs;
    private final ObjectMapper objectMapper;

    public TripChatOrchestrator(LlmStreamPort llm, TripChatToolService toolService,
            TripBriefService briefs, ObjectMapper objectMapper) {
        this.llm = llm;
        this.toolService = toolService;
        this.briefs = briefs;
        this.objectMapper = objectMapper;
    }

    /**
     * The trip turn as a cold stream of provider events, with the intake tools executed inline.
     *
     * <p>A planner target is a routing mistake — {@code ChatTurnService} sends those to
     * {@code PlannerChatOrchestrator} — so it degrades to a plain, tool-free stream rather than
     * offering trip tools on a surface that has no trip.
     */
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

    /**
     * Runs the tool the model asked for, in its own transaction.
     *
     * @return the tool result and {@code brief_updated} event on success, or a single terminal
     *         {@link LlmEvent.StreamError} carrying the domain code on any refusal
     */
    private Outcome execute(ToolCall call, Round round) {
        try {
            TripChatToolService.Result result = run(call, round);
            List<LlmEvent> events = List.of(
                    new LlmEvent.ToolResult(call.id(), result.payloadJson()),
                    new LlmEvent.DomainEvent(BRIEF_UPDATED,
                            Map.of(TRIP_ID_KEY, round.tripId().toString())));
            return Outcome.ok(events, result.payloadJson());
        } catch (DomainException failure) {
            log.warn("trip_tool_rejected tool={} code={} detail={}", call.name(), failure.code(),
                    failure.details());
            return Outcome.failed(
                    new LlmEvent.StreamError(failure.code(), failure.getMessage(), failure.details()));
        }
    }

    private TripChatToolService.Result run(ToolCall call, Round round) {
        TripChatToolService.Context context = new TripChatToolService.Context(
                round.turn().conversationId(), round.tripId(), call.id(), call.name(),
                round.turn().user());
        if (TripChatTools.UPDATE_TRIP_BRIEF.equals(call.name())) {
            return toolService.applyUpdate(context,
                    UpdateTripBriefArgs.parse(call.name(), call.inputJson(), objectMapper));
        }
        if (TripChatTools.ANSWER_CLARIFICATION.equals(call.name())) {
            return toolService.applyClarification(context,
                    AnswerClarificationArgs.parse(call.name(), call.inputJson(), objectMapper));
        }
        throw ValidationFailedException.field("tool_name", "unknown trip tool: " + call.name());
    }

    /** The prompt for the next round: this round's assistant turn, then each tool result. */
    private static Prompt continuation(Round round, String assistantText,
            List<PromptMessage> toolResults) {
        List<PromptMessage> messages = new ArrayList<>(round.prompt().messages());
        messages.add(PromptMessage.assistant(assistantText.isBlank() ? "(calling tools)" : assistantText));
        messages.addAll(toolResults);
        return Prompt.adHoc(List.copyOf(messages));
    }

    /**
     * The mutable state of one provider round: the calls being assembled, the text that will become
     * the assistant turn if the loop continues, the tool results to feed forward, and how the round
     * ended. Not shared across rounds — a fresh one is created per {@link #round(Round)}.
     */
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

        /** Recorded, not forwarded: {@link #continuation()} decides whether it ends the turn. */
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

    /** The immutable frame of one round: which trip, which prompt, which tools, and how deep. */
    private record Round(ChatTurn turn, UUID tripId, Prompt prompt, List<ToolSpec> tools, int number) {

        private Round next(Prompt nextPrompt) {
            return new Round(turn, tripId, nextPrompt, tools, number + 1);
        }
    }

    /** The result of executing one tool: the events to emit, and the payload to feed forward. */
    private record Outcome(List<LlmEvent> events, String toolResultPayload, boolean failed) {

        private static Outcome ok(List<LlmEvent> events, String toolResultPayload) {
            return new Outcome(events, toolResultPayload, false);
        }

        private static Outcome failed(LlmEvent.StreamError error) {
            return new Outcome(List.of(error), null, true);
        }
    }

    /** One tool call, accumulated as its argument bytes stream in. */
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
