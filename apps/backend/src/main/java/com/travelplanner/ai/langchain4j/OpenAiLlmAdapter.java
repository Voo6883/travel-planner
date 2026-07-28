package com.travelplanner.ai.langchain4j;

import com.travelplanner.config.AiProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.time.Duration;

/**
 * The OpenAI provider. Constructed by {@code AiConfig} only when selected, for the same reason as
 * {@link AnthropicLlmAdapter}: with the default {@code stub} provider it is never instantiated, so
 * CI never needs {@code OPENAI_API_KEY}.
 *
 * <p>Streaming usage has to be asked for. OpenAI omits token counts from a streamed response unless
 * {@code stream_options.include_usage} is set, and LangChain4j exposes that through
 * {@code customParameters}. Without it every streamed call would report zero tokens and
 * {@code ai_call_log} would be quietly useless for exactly the traffic that dominates — which is the
 * failure ADR 007's {@code Usage} event exists to prevent.
 */
final class OpenAiLlmAdapter extends LangChain4jLlmAdapter {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;

    OpenAiLlmAdapter(AiProperties.OpenAi settings, Duration timeout) {
        super(AiProperties.OPENAI_PROVIDER);
        OpenAiChatModel.OpenAiChatModelBuilder blocking = OpenAiChatModel.builder()
                .apiKey(settings.getApiKey())
                .modelName(settings.getModel())
                .maxCompletionTokens(settings.getMaxOutputTokens())
                .temperature(settings.getTemperature())
                .timeout(timeout);
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder streaming =
                OpenAiStreamingChatModel.builder()
                        .apiKey(settings.getApiKey())
                        .modelName(settings.getModel())
                        .maxCompletionTokens(settings.getMaxOutputTokens())
                        .temperature(settings.getTemperature())
                        .timeout(timeout)
                        .customParameters(java.util.Map.of(
                                "stream_options", java.util.Map.of("include_usage", true)));
        if (!settings.getBaseUrl().isBlank()) {
            blocking.baseUrl(settings.getBaseUrl());
            streaming.baseUrl(settings.getBaseUrl());
        }
        this.chatModel = blocking.build();
        this.streamingChatModel = streaming.build();
    }

    @Override
    protected ChatModel chatModel() {
        return chatModel;
    }

    @Override
    protected StreamingChatModel streamingChatModel() {
        return streamingChatModel;
    }
}
