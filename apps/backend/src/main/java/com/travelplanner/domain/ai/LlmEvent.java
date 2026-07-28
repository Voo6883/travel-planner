package com.travelplanner.domain.ai;

import java.util.Map;
import java.util.Objects;

/**
 * Everything a model can emit while producing a turn (ADR 007).
 *
 * <p><strong>This union is the reason {@code LlmPort} does not return {@code Flux<String>}.</strong>
 * PLAN §5.1 originally specified a token stream; ADR 007 supersedes it. A stream of strings
 * structurally cannot carry a tool-use delta, a stop reason, token usage, or a mid-stream failure —
 * so the locked {@code trip_created} behaviour (§3.2) is unimplementable on top of one. Both
 * Anthropic and OpenAI stream <em>events</em>; flattening them to text discards exactly the parts
 * the product needs.
 *
 * <p>It is {@code sealed} on purpose. The frontend half of this contract is hand-authored
 * ({@code lib/api/chat-stream.ts}, the single documented codegen exception), so the backend half has
 * to be a closed type: adding a variant then breaks every {@code switch} at compile time instead of
 * silently reaching a client that cannot render it.
 *
 * <p>Nested records rather than nine files: a sealed hierarchy is only readable when the whole union
 * is visible at once, and nesting makes "permitted subtypes" a language guarantee rather than a
 * convention about file placement.
 *
 * <h2>What this type is not</h2>
 *
 * <ul>
 *   <li><strong>Not the wire format.</strong> SSE framing, ids, heartbeat, and resume belong to
 *       task 20. Nothing here knows what an SSE frame looks like.</li>
 *   <li><strong>Not a JSON envelope.</strong> ADR 007 rejects JSON-in-text-frames because it pushes
 *       protocol parsing into every consumer and loses type safety at the port.</li>
 * </ul>
 */
public sealed interface LlmEvent {

    /**
     * A chunk of assistant prose. The only variant a naive "token stream" could have expressed.
     *
     * <p>Rendered as plain text while streaming; markdown is sanitised server-side on persist and
     * only then rendered as markdown (ADR 007 Consequences). Never concatenate these into a value
     * that is treated as a fact — grounded output goes through structured completion.
     */
    record TextDelta(String text) implements LlmEvent {

        public TextDelta {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * The model has begun requesting a tool call. Arrives <em>before</em> any argument bytes, so a
     * UI can show "looking that up…" while the arguments are still streaming.
     *
     * @param toolCallId provider-assigned correlation id; every later event for this call repeats it
     * @param name the tool the model chose, which must still be validated against the registry —
     *     a model may name a tool that does not exist
     */
    record ToolUseStart(String toolCallId, String name) implements LlmEvent {

        public ToolUseStart {
            Objects.requireNonNull(toolCallId, "toolCallId");
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * A fragment of the tool's JSON arguments.
     *
     * <p>Deliberately a raw fragment and not parsed JSON: a partial object is not valid JSON, and
     * pretending otherwise would force every adapter to buffer. Consumers accumulate by
     * {@code toolCallId} and parse once {@link ToolUseEnd} arrives.
     */
    record ToolInputDelta(String toolCallId, String jsonChunk) implements LlmEvent {

        public ToolInputDelta {
            Objects.requireNonNull(toolCallId, "toolCallId");
            Objects.requireNonNull(jsonChunk, "jsonChunk");
        }
    }

    /**
     * The arguments for {@code toolCallId} are complete. This is the point at which the accumulated
     * JSON may be schema-validated — and it must be, before anything executes
     * ({@code docs/AGENT-HARNESS.md} §4: never execute a state-mutating tool from free text).
     */
    record ToolUseEnd(String toolCallId) implements LlmEvent {

        public ToolUseEnd {
            Objects.requireNonNull(toolCallId, "toolCallId");
        }
    }

    /**
     * The outcome of the orchestrator actually running the tool, fed back into the stream.
     *
     * @param payload the serialised result. A {@code String} rather than a parsed tree because the
     *     domain layer may not depend on Jackson; the orchestrator owns serialisation.
     */
    record ToolResult(String toolCallId, String payload) implements LlmEvent {

        public ToolResult {
            Objects.requireNonNull(toolCallId, "toolCallId");
            Objects.requireNonNull(payload, "payload");
        }
    }

    /**
     * A typed product event such as {@code trip_created} (PLAN §3.2).
     *
     * <p><strong>No provider adapter may ever construct one of these.</strong> ADR 007 is explicit:
     * a domain event is emitted by the orchestrator <em>after</em> the corresponding tool has
     * committed — never parsed out of assistant prose. Prose saying "I've created your trip" is not
     * evidence that a row exists, and treating it as such is how a UI navigates to a trip that was
     * never persisted.
     */
    record DomainEvent(String type, Map<String, Object> payload) implements LlmEvent {

        public DomainEvent {
            Objects.requireNonNull(type, "type");
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    /**
     * Token accounting for the turn, normalised across providers.
     *
     * <p>This variant is what makes {@code ai_call_log} (PLAN §5.3) implementable at the port
     * boundary — ADR 007 Consequences names it as previously unimplementable. Without usage on the
     * stream, cost and token metrics would have to be re-derived by a caller that does not know
     * which provider answered.
     *
     * @param cachedTokens prompt tokens served from a provider-side cache. Anthropic reports
     *     cache-read tokens, OpenAI reports cached input tokens; both land here, and {@code 0} means
     *     "none or not reported" rather than "unknown".
     */
    record Usage(int inputTokens, int outputTokens, int cachedTokens) implements LlmEvent {

        public Usage {
            inputTokens = Math.max(0, inputTokens);
            outputTokens = Math.max(0, outputTokens);
            cachedTokens = Math.max(0, cachedTokens);
        }

        public static Usage none() {
            return new Usage(0, 0, 0);
        }

        /** Billable prompt tokens plus completion tokens. Cached tokens are already in the input. */
        public int totalTokens() {
            return inputTokens + outputTokens;
        }
    }

    /** The turn finished normally. Always the last event of a successful stream. */
    record Done(StopReason stopReason) implements LlmEvent {

        public Done {
            Objects.requireNonNull(stopReason, "stopReason");
        }
    }

    /**
     * A terminal failure carrying the PLAN §6.1 error envelope.
     *
     * <p>This exists because HTTP status is no longer available: once the response has committed to
     * {@code 200} and begun streaming, a failure cannot be signalled as a {@code 502}. ADR 007
     * requires an explicit error frame rather than a bare stream abort, so the client can tell "the
     * model failed" apart from "the network dropped" — the two need different UI and only one is
     * worth retrying automatically.
     *
     * <p>{@code code} must be registered in {@code api/openapi/errors.yaml}; an unregistered code
     * renders as a raw identifier to the user.
     */
    record StreamError(String code, String message, Map<String, Object> details) implements LlmEvent {

        public StreamError {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
            details = details == null ? Map.of() : Map.copyOf(details);
        }

        public static StreamError of(String code, String message) {
            return new StreamError(code, message, Map.of());
        }
    }
}
