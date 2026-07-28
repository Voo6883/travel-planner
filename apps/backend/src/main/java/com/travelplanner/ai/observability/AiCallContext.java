package com.travelplanner.ai.observability;

import com.travelplanner.domain.ai.AiOperation;
import java.util.UUID;

/**
 * What the recorder needs to know before a call runs, bundled so
 * {@code AiCallRecorder.record(...)} stays within the ≤3-parameter rule (PLAN §4.0.4, whose stated
 * remedy is exactly this: "bundle into a {@code *Query}/{@code *Command}/{@code *Context}").
 *
 * @param userId nullable — background jobs and system calls have no user
 * @param promptHash produced by {@link PromptHasher}; never the prompt itself
 */
public record AiCallContext(String feature, AiOperation operation, String provider, String model,
        UUID userId, String promptHash) {

    public static AiCallContext of(String feature, AiOperation operation, String provider) {
        return new AiCallContext(feature, operation, provider, "", null, "");
    }

    public AiCallContext withModel(String value) {
        return new AiCallContext(feature, operation, provider, value, userId, promptHash);
    }

    public AiCallContext withPromptHash(String value) {
        return new AiCallContext(feature, operation, provider, model, userId, value);
    }

    public AiCallContext withUserId(UUID value) {
        return new AiCallContext(feature, operation, provider, model, value, promptHash);
    }
}
