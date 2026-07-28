package com.travelplanner.ai.langchain4j;

import com.travelplanner.config.AiProperties;
import com.travelplanner.domain.port.EmbeddingPort;
import com.travelplanner.domain.port.LlmPort;
import java.time.Duration;

/**
 * The package's only public surface.
 *
 * <p>Every class in {@code ai.langchain4j} is package-private, so LangChain4j types cannot leak
 * through a return type or a constructor signature even by accident — the AGENTS.md import rule
 * ("no LangChain4j outside {@code ai/langchain4j/}") becomes a compiler guarantee rather than a
 * review habit. {@code AiConfig} sees two factory methods returning project ports and nothing else.
 */
public final class LangChain4jProviderFactory {

    private LangChain4jProviderFactory() {
    }

    /** @param timeout the per-call budget; a hung provider must not hold a thread indefinitely */
    public static LlmPort anthropic(AiProperties.Anthropic settings, Duration timeout) {
        return new AnthropicLlmAdapter(settings, timeout);
    }

    public static LlmPort openAi(AiProperties.OpenAi settings, Duration timeout) {
        return new OpenAiLlmAdapter(settings, timeout);
    }

    /**
     * @param apiKey passed separately because embeddings and chat are configured independently
     *     (PLAN §5.4: chat may switch providers, embeddings may not) yet share one OpenAI credential
     */
    public static EmbeddingPort openAiEmbeddings(AiProperties.Embeddings settings, String apiKey,
            Duration timeout) {
        return new OpenAiEmbeddingAdapter(settings, apiKey, timeout);
    }
}
