package com.travelplanner.ai.langchain4j;

import com.travelplanner.ai.client.LlmProvider;
import com.travelplanner.domain.ai.LlmCompletion;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.LlmToolCall;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.StopReason;
import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.exception.AiProviderException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * The provider-neutral half of both LangChain4j adapters.
 *
 * <p>Anthropic and OpenAI subclasses supply two beans and a name; everything below — request
 * mapping, the {@link LlmEvent} bridge, error normalisation, cancellation — is identical for both,
 * because LangChain4j has already reconciled the two SSE dialects behind
 * {@code StreamingChatResponseHandler}. Duplicating this per provider is how the two adapters would
 * drift on the cases that matter least often and cost most: a stop reason, a tool-call id, a
 * mid-stream error.
 *
 * <p>Structured output is deliberately <em>not</em> implemented here. It is composed one layer up by
 * {@code ai/structured/StructuredOutputRunner}, so the schema instruction, the validation, and the
 * bounded repair attempt are written once for every provider including the stub — rather than
 * three times, differently.
 */
abstract class LangChain4jLlmAdapter implements LlmProvider {

    private final String providerName;
    private final String modelName;

    protected LangChain4jLlmAdapter(String providerName, String modelName) {
        this.providerName = providerName;
        this.modelName = modelName;
    }

    /** The blocking model used by {@link #complete} and {@link #completeWithTools}. */
    protected abstract ChatModel chatModel();

    /** The event-emitting model used by {@link #stream}. */
    protected abstract StreamingChatModel streamingChatModel();

    @Override
    public final String providerName() {
        return providerName;
    }

    /**
     * The model the two {@code ChatModel} instances were built with. Held as a field rather than read
     * back off a response, because a failed call still has to be priced and has no response to read.
     */
    @Override
    public final String modelName() {
        return modelName;
    }

    @Override
    public String complete(Prompt prompt, LlmOptions options) {
        return completeWithTools(prompt, List.of(), options).text();
    }

    @Override
    public LlmCompletion completeWithTools(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
        ChatRequest request = LangChain4jRequestMapper.toRequest(prompt, tools, options);
        try {
            return toCompletion(chatModel().chat(request));
        } catch (RuntimeException failure) {
            throw ProviderErrorMapper.map(providerName, failure);
        }
    }

    @Override
    public Flux<LlmEvent> stream(Prompt prompt, LlmOptions options) {
        return stream(prompt, List.of(), options);
    }

    @Override
    public Flux<LlmEvent> stream(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
        return Flux.create(sink -> {
            ChatRequest request;
            try {
                request = LangChain4jRequestMapper.toRequest(prompt, tools, options);
            } catch (RuntimeException failure) {
                // Mapping failed before a byte was sent, so there is no 200 to be stuck behind yet.
                // Erroring the Flux lets task 20 answer with a real HTTP status instead of opening a
                // stream whose only content is an error frame.
                sink.error(ProviderErrorMapper.map(providerName, failure));
                return;
            }
            LlmEventBridge bridge = new LlmEventBridge(sink, providerName);
            try {
                streamingChatModel().chat(request, bridge);
            } catch (RuntimeException failure) {
                bridge.onError(failure);
            }
        }, reactor.core.publisher.FluxSink.OverflowStrategy.BUFFER);
    }

    private LlmCompletion toCompletion(ChatResponse response) {
        if (response == null || response.aiMessage() == null) {
            throw AiProviderException.unavailable(providerName + ": empty response");
        }
        List<LlmToolCall> toolCalls = response.aiMessage().toolExecutionRequests().stream()
                .map(request -> new LlmToolCall(request.id(), request.name(), request.arguments()))
                .toList();
        return new LlmCompletion(response.aiMessage().text(), toolCalls,
                TokenUsageMapper.toUsage(response.tokenUsage()), stopReasonOf(response, toolCalls));
    }

    private static StopReason stopReasonOf(ChatResponse response, List<LlmToolCall> toolCalls) {
        if (!toolCalls.isEmpty()) {
            return StopReason.TOOL_USE;
        }
        if (response.finishReason() == null) {
            return StopReason.END_TURN;
        }
        return switch (response.finishReason()) {
            case STOP -> StopReason.END_TURN;
            case LENGTH -> StopReason.MAX_TOKENS;
            case TOOL_EXECUTION -> StopReason.TOOL_USE;
            case CONTENT_FILTER -> StopReason.CONTENT_FILTERED;
            case OTHER -> StopReason.OTHER;
        };
    }
}
