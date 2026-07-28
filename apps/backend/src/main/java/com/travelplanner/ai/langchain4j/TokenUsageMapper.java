package com.travelplanner.ai.langchain4j;

import com.travelplanner.domain.ai.LlmEvent;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Vendor token accounting to {@link LlmEvent.Usage} — the one place the two providers genuinely
 * disagree and cannot be normalised by LangChain4j alone.
 *
 * <p>Prompt and completion counts are already common. Cached tokens are not: Anthropic reports
 * {@code cache_read_input_tokens} and {@code cache_creation_input_tokens} on its own
 * {@code TokenUsage} subclass, OpenAI reports {@code prompt_tokens_details.cached_tokens} on its
 * own. Both mean "prompt tokens that were not charged at full rate", and both are the difference
 * between a cost estimate that is roughly right and one that overstates a cached conversation
 * severalfold — which is why {@code LlmEvent.Usage} has a third field at all.
 *
 * <p>Only the cache <em>read</em> count is reported for Anthropic. Cache <em>creation</em> tokens are
 * charged at a premium rather than discounted, so folding them into "cached" would understate the
 * bill in exactly the case where it is highest.
 */
final class TokenUsageMapper {

    private TokenUsageMapper() {
    }

    static LlmEvent.Usage toUsage(TokenUsage usage) {
        if (usage == null) {
            return LlmEvent.Usage.none();
        }
        return new LlmEvent.Usage(
                orZero(usage.inputTokenCount()),
                orZero(usage.outputTokenCount()),
                cachedTokens(usage));
    }

    private static int cachedTokens(TokenUsage usage) {
        if (usage instanceof AnthropicTokenUsage anthropic) {
            return orZero(anthropic.cacheReadInputTokens());
        }
        if (usage instanceof OpenAiTokenUsage openAi && openAi.inputTokensDetails() != null) {
            return orZero(openAi.inputTokensDetails().cachedTokens());
        }
        return 0;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
