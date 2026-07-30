package com.travelplanner.ai.langchain4j;

import com.travelplanner.config.AiProperties;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * The Anthropic provider. <strong>One of only two classes that know Anthropic exists</strong> — the
 * other is {@link ProviderErrorMapper}, and neither is visible outside this package.
 *
 * <p>Not a Spring bean. {@code AiConfig} constructs it only when the provider is actually selected,
 * which keeps the "no live keys in CI" rule structural: with the default {@code stub} provider this
 * class is never instantiated, so no test can reach the network and no build can start needing
 * {@code ANTHROPIC_API_KEY}.
 *
 * <p>{@code maxTokens} is set at construction rather than left to the SDK. Anthropic <em>requires</em>
 * {@code max_tokens} on every request and rejects the call without it — unlike OpenAI, where it is
 * optional. That asymmetry is exactly the kind of thing that must not reach a caller.
 */
final class AnthropicLlmAdapter extends LangChain4jLlmAdapter {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;

    AnthropicLlmAdapter(AiProperties.Anthropic settings, java.time.Duration timeout) {
        super(AiProperties.ANTHROPIC_PROVIDER, settings.getModel());
        AnthropicChatModel.AnthropicChatModelBuilder blocking = AnthropicChatModel.builder()
                .apiKey(settings.getApiKey())
                .modelName(settings.getModel())
                .maxTokens(settings.getMaxOutputTokens())
                .temperature(settings.getTemperature())
                .timeout(timeout);
        AnthropicStreamingChatModel.AnthropicStreamingChatModelBuilder streaming =
                AnthropicStreamingChatModel.builder()
                        .apiKey(settings.getApiKey())
                        .modelName(settings.getModel())
                        .maxTokens(settings.getMaxOutputTokens())
                        .temperature(settings.getTemperature())
                        .timeout(timeout);
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
