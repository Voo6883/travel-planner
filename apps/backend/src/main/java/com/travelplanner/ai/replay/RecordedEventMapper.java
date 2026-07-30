package com.travelplanner.ai.replay;

import com.travelplanner.ai.replay.RecordedExchange.RecordedEvent;
import com.travelplanner.ai.replay.RecordedExchange.RecordedUsage;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.StopReason;

/**
 * The one place that translates between {@code LlmEvent} and the flat fixture shape.
 *
 * <p>Both directions live here so they cannot drift: a recorder that wrote a field the replayer does
 * not read produces a fixture that replays as a subtly different stream, and the symptom is a test
 * that passes against a recording of something else. Keeping the pair adjacent makes an omission
 * visible in one file.
 *
 * <p><strong>{@link #toDomain} has no default branch.</strong> An unknown discriminant throws with the
 * offending value rather than being skipped, because a silently dropped event is the failure this
 * whole mechanism exists to avoid — a stream missing its {@code Usage} frame replays as a zero-token
 * call, which is precisely the defect the 2026-07-29 review found in the router.
 *
 * <p>The vocabulary is deliberately the SSE wire's lower-snake names ({@code text_delta},
 * {@code tool_use_start}). A fixture is then readable beside a captured SSE log, which is what
 * somebody does when a recording and production disagree.
 */
final class RecordedEventMapper {

    static final String TEXT_DELTA = "text_delta";
    static final String TOOL_USE_START = "tool_use_start";
    static final String TOOL_INPUT_DELTA = "tool_input_delta";
    static final String TOOL_USE_END = "tool_use_end";
    static final String TOOL_RESULT = "tool_result";
    static final String USAGE = "usage";
    static final String DONE = "done";
    static final String ERROR = "error";

    private RecordedEventMapper() {
    }

    /**
     * Fixture → domain event.
     *
     * @throws IllegalArgumentException on an unknown type, or on a known type missing a field it needs
     */
    static LlmEvent toDomain(RecordedEvent event) {
        return switch (event.type()) {
            case TEXT_DELTA -> new LlmEvent.TextDelta(require(event.text(), event, "text"));
            case TOOL_USE_START -> new LlmEvent.ToolUseStart(
                    require(event.toolCallId(), event, "toolCallId"), require(event.name(), event, "name"));
            case TOOL_INPUT_DELTA -> new LlmEvent.ToolInputDelta(
                    require(event.toolCallId(), event, "toolCallId"),
                    require(event.jsonChunk(), event, "jsonChunk"));
            case TOOL_USE_END -> new LlmEvent.ToolUseEnd(require(event.toolCallId(), event, "toolCallId"));
            case TOOL_RESULT -> new LlmEvent.ToolResult(
                    require(event.toolCallId(), event, "toolCallId"), require(event.payload(), event, "payload"));
            case USAGE -> usage(event);
            case DONE -> new LlmEvent.Done(event.stopReason() == null ? StopReason.END_TURN : event.stopReason());
            case ERROR -> LlmEvent.StreamError.of(
                    require(event.errorCode(), event, "errorCode"),
                    event.errorMessage() == null ? "" : event.errorMessage());
            // No default that shrugs. A dropped event replays as a stream that is missing something,
            // and the two most valuable fixtures — a usage frame and an error frame — are exactly the
            // ones whose absence looks like success.
            default -> throw new IllegalArgumentException(
                    "unknown recorded event type '" + event.type() + "'. Known: " + TEXT_DELTA + ", "
                            + TOOL_USE_START + ", " + TOOL_INPUT_DELTA + ", " + TOOL_USE_END + ", "
                            + TOOL_RESULT + ", " + USAGE + ", " + DONE + ", " + ERROR);
        };
    }

    /**
     * Domain event → fixture.
     *
     * <p>{@code DomainEvent} is deliberately unsupported: ADR 007 forbids an adapter from ever emitting
     * one — the orchestrator emits it after a tool commits — so a recording containing one would
     * describe a provider stream that cannot exist, and replaying it would let an orchestrator bug pass
     * its own test.
     */
    static RecordedEvent toFixture(LlmEvent event) {
        return switch (event) {
            case LlmEvent.TextDelta delta -> of(TEXT_DELTA).text(delta.text()).build();
            case LlmEvent.ToolUseStart start ->
                    of(TOOL_USE_START).toolCallId(start.toolCallId()).name(start.name()).build();
            case LlmEvent.ToolInputDelta delta ->
                    of(TOOL_INPUT_DELTA).toolCallId(delta.toolCallId()).jsonChunk(delta.jsonChunk()).build();
            case LlmEvent.ToolUseEnd end -> of(TOOL_USE_END).toolCallId(end.toolCallId()).build();
            case LlmEvent.ToolResult result ->
                    of(TOOL_RESULT).toolCallId(result.toolCallId()).payload(result.payload()).build();
            case LlmEvent.Usage seen -> of(USAGE)
                    .usage(new RecordedUsage(seen.inputTokens(), seen.outputTokens(), seen.cachedTokens()))
                    .build();
            case LlmEvent.Done done -> of(DONE).stopReason(done.stopReason()).build();
            case LlmEvent.StreamError error ->
                    of(ERROR).errorCode(error.code()).errorMessage(error.message()).build();
            case LlmEvent.DomainEvent ignored -> throw new IllegalArgumentException(
                    "a provider stream cannot contain a DomainEvent (ADR 007): the orchestrator emits "
                            + "those after a tool commits, so recording one would describe a stream no "
                            + "provider produces and would let an orchestrator bug pass its own test");
        };
    }

    private static LlmEvent.Usage usage(RecordedEvent event) {
        RecordedUsage recorded = event.usage() == null ? RecordedUsage.none() : event.usage();
        return new LlmEvent.Usage(recorded.inputTokens(), recorded.outputTokens(), recorded.cachedTokens());
    }

    /**
     * A field a discriminant promises must actually be there.
     *
     * <p>A {@code null} would otherwise reach a record constructor as a {@code NullPointerException}
     * naming a parameter, with no indication of which fixture is malformed. This names the type and
     * the field, which is the difference between a fix and a search.
     */
    private static String require(String value, RecordedEvent event, String field) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "recorded event of type '" + event.type() + "' is missing required field '" + field + "'");
        }
        return value;
    }

    private static Builder of(String type) {
        return new Builder(type);
    }

    /** Assembles a flat {@link RecordedEvent} without ten positional nulls at each call site. */
    private static final class Builder {

        private final String type;
        private String text;
        private String toolCallId;
        private String name;
        private String jsonChunk;
        private String payload;
        private RecordedUsage usage;
        private StopReason stopReason;
        private String errorCode;
        private String errorMessage;

        private Builder(String type) {
            this.type = type;
        }

        private Builder text(String value) {
            this.text = value;
            return this;
        }

        private Builder toolCallId(String value) {
            this.toolCallId = value;
            return this;
        }

        private Builder name(String value) {
            this.name = value;
            return this;
        }

        private Builder jsonChunk(String value) {
            this.jsonChunk = value;
            return this;
        }

        private Builder payload(String value) {
            this.payload = value;
            return this;
        }

        private Builder usage(RecordedUsage value) {
            this.usage = value;
            return this;
        }

        private Builder stopReason(StopReason value) {
            this.stopReason = value;
            return this;
        }

        private Builder errorCode(String value) {
            this.errorCode = value;
            return this;
        }

        private Builder errorMessage(String value) {
            this.errorMessage = value;
            return this;
        }

        private RecordedEvent build() {
            return new RecordedEvent(type, text, toolCallId, name, jsonChunk, payload, usage, stopReason,
                    errorCode, errorMessage);
        }
    }
}
