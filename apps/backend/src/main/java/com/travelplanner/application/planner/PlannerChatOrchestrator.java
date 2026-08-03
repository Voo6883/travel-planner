package com.travelplanner.application.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.application.chat.ChatTurn;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.port.LlmPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/** Runs chat turns and executes planner-only tools requested by the model. */
@Service
@RequiresDatabase
public class PlannerChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PlannerChatOrchestrator.class);
    private static final String FEATURE = "chat";
    private static final String TRIP_CREATED = "trip_created";
    private static final String TRIP_ID_KEY = "trip_id";

    private final LlmPort llm;
    private final CreateTripHandoffService handoff;
    private final ObjectMapper objectMapper;

    public PlannerChatOrchestrator(LlmPort llm, CreateTripHandoffService handoff,
            ObjectMapper objectMapper) {
        this.llm = llm;
        this.handoff = handoff;
        this.objectMapper = objectMapper;
    }

    public Flux<LlmEvent> stream(ChatTurn turn) {
        if (!turn.target().isPlanner()) {
            return llm.stream(turn.prompt(), LlmOptions.forFeature(FEATURE));
        }
        PlannerToolBuffer tools = new PlannerToolBuffer(turn);
        return llm.stream(turn.prompt(), PlannerTools.specs(), LlmOptions.forFeature(FEATURE))
                .concatMap(event -> Flux.fromIterable(tools.handle(event)));
    }

    private List<LlmEvent> execute(ToolCall call, ChatTurn turn) {
        try {
            CreateTripArgs args = CreateTripArgs.parse(call.name(), call.inputJson(), objectMapper);
            var command = new CreateTripHandoffService.Command(turn.conversationId(), call.id(), args);
            CreateTripHandoffService.Result result = handoff.createTrip(command, turn.user());
            return toolSuccess(call.id(), result);
        } catch (ValidationFailedException failure) {
            log.warn("planner_tool_rejected tool={} reason={}", call.name(), failure.details());
            return List.of(new LlmEvent.StreamError(failure.code(), failure.getMessage(), failure.details()));
        }
    }

    private static List<LlmEvent> toolSuccess(String toolCallId, CreateTripHandoffService.Result result) {
        List<LlmEvent> events = new ArrayList<>();
        events.add(new LlmEvent.ToolResult(toolCallId, result.payloadJson()));
        events.add(new LlmEvent.DomainEvent(TRIP_CREATED, Map.of(TRIP_ID_KEY, result.tripId().toString())));
        return List.copyOf(events);
    }

    private final class PlannerToolBuffer {

        private final ChatTurn turn;
        private final Map<String, ToolCall> calls = new HashMap<>();

        private PlannerToolBuffer(ChatTurn turn) {
            this.turn = turn;
        }

        private List<LlmEvent> handle(LlmEvent event) {
            return switch (event) {
                case LlmEvent.ToolUseStart start -> start(start);
                case LlmEvent.ToolInputDelta delta -> append(delta);
                case LlmEvent.ToolUseEnd end -> end(end);
                default -> List.of(event);
            };
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
            List<LlmEvent> events = new ArrayList<>();
            events.add(end);
            events.addAll(execute(call, turn));
            return List.copyOf(events);
        }
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
