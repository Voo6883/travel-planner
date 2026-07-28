package com.travelplanner.ai.langchain4j;

import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.StopReason;
import com.travelplanner.domain.exception.AiProviderException;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.HashSet;
import java.util.Set;
import reactor.core.publisher.FluxSink;

/**
 * Adapts a LangChain4j callback stream onto a Reactor {@link FluxSink} of {@link LlmEvent}
 * (ADR 007).
 *
 * <p>This class is the concrete answer to "how does a provider's native stream map onto the union".
 * Both Anthropic and OpenAI arrive here through the <em>same</em> callbacks, because LangChain4j has
 * already normalised the two SSE dialects — Anthropic's {@code content_block_delta} /
 * {@code input_json_delta} / {@code message_delta} and OpenAI's {@code choices[].delta.tool_calls} —
 * into one handler interface. Writing this once rather than per provider is the reason
 * {@code AnthropicLlmAdapter} and {@code OpenAiLlmAdapter} are twenty lines each.
 *
 * <h2>Ordering guarantee</h2>
 *
 * <p>Providers do not emit an explicit "tool call started" event; they emit the first argument
 * fragment with a name attached. {@link ToolUseStart} is therefore synthesised on first sight of a
 * call id, which is what lets a UI show "looking that up…" before arguments finish streaming. The id
 * set is what makes it exactly once.
 *
 * <h2>Terminal behaviour</h2>
 *
 * <p>A failure emits {@link LlmEvent.StreamError} and then <em>completes</em> the Flux rather than
 * erroring it. ADR 007 requires an explicit error frame, never a bare abort: by the time a stream is
 * running the HTTP response is already {@code 200}, and a subscriber that sees only a truncated
 * stream cannot tell a model failure from a dropped connection. The two need different UI, and only
 * one is worth retrying.
 */
final class LlmEventBridge implements StreamingChatResponseHandler {

    private final FluxSink<LlmEvent> sink;
    private final String provider;
    private final Set<String> startedToolCalls = new HashSet<>();

    LlmEventBridge(FluxSink<LlmEvent> sink, String provider) {
        this.sink = sink;
        this.provider = provider;
    }

    @Override
    public void onPartialResponse(String partialResponse) {
        if (partialResponse != null && !partialResponse.isEmpty()) {
            sink.next(new LlmEvent.TextDelta(partialResponse));
        }
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall) {
        String id = partialToolCall.id();
        if (id == null) {
            return;
        }
        if (startedToolCalls.add(id)) {
            sink.next(new LlmEvent.ToolUseStart(id, nullSafe(partialToolCall.name())));
        }
        String chunk = partialToolCall.partialArguments();
        if (chunk != null && !chunk.isEmpty()) {
            sink.next(new LlmEvent.ToolInputDelta(id, chunk));
        }
    }

    @Override
    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
        String id = completeToolCall.toolExecutionRequest().id();
        if (id == null) {
            return;
        }
        if (startedToolCalls.add(id)) {
            // A provider that batched the whole call into one frame never produced a partial, so the
            // start event still has to be emitted — otherwise a consumer sees an end for a call it
            // was never told about.
            sink.next(new LlmEvent.ToolUseStart(id, completeToolCall.toolExecutionRequest().name()));
            String arguments = completeToolCall.toolExecutionRequest().arguments();
            if (arguments != null && !arguments.isEmpty()) {
                sink.next(new LlmEvent.ToolInputDelta(id, arguments));
            }
        }
        sink.next(new LlmEvent.ToolUseEnd(id));
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        // Usage before Done, always. A consumer that stops reading at Done would otherwise never see
        // the token counts, and ai_call_log would have nothing to record.
        sink.next(TokenUsageMapper.toUsage(completeResponse.tokenUsage()));
        sink.next(new LlmEvent.Done(toStopReason(completeResponse)));
        sink.complete();
    }

    @Override
    public void onError(Throwable error) {
        AiProviderException normalised = ProviderErrorMapper.map(provider, error);
        sink.next(new LlmEvent.StreamError(normalised.code(), normalised.getMessage(),
                normalised.details()));
        sink.complete();
    }

    private static StopReason toStopReason(ChatResponse response) {
        if (response.finishReason() == null) {
            return response.aiMessage() != null && response.aiMessage().hasToolExecutionRequests()
                    ? StopReason.TOOL_USE
                    : StopReason.END_TURN;
        }
        return switch (response.finishReason()) {
            case STOP -> StopReason.END_TURN;
            case LENGTH -> StopReason.MAX_TOKENS;
            case TOOL_EXECUTION -> StopReason.TOOL_USE;
            case CONTENT_FILTER -> StopReason.CONTENT_FILTERED;
            case OTHER -> StopReason.OTHER;
        };
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
