package com.travelplanner.ai.structured;

import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import java.util.Objects;

/**
 * One structured-completion request.
 *
 * <p>A bundle, per PLAN §4.0.4's remedy for the ≤3-parameter rule, and it also keeps the generic
 * parameter attached to a single value — so a caller cannot pass a schema that describes one type
 * while asking to deserialise another.
 *
 * @param jsonSchema the schema the model is told to satisfy. Supplied by the caller rather than
 *     derived from {@code type} by reflection: schema generation would pull another dependency in,
 *     and PLAN §6 item 3 wants the schema derived from the same DTO the contract uses — which is
 *     task 19's decision to make for its own types, not this platform's to pre-empt.
 */
public record StructuredRequest<T>(Prompt prompt, Class<T> type, String jsonSchema,
        LlmOptions options) {

    public StructuredRequest {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(jsonSchema, "jsonSchema");
        options = options == null ? LlmOptions.defaults() : options;
    }

    public static <T> StructuredRequest<T> of(Prompt prompt, Class<T> type, String jsonSchema) {
        return new StructuredRequest<>(prompt, type, jsonSchema, LlmOptions.defaults());
    }
}
