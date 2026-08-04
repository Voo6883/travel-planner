package com.travelplanner.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Narratives + prompt/model metadata from {@link com.travelplanner.domain.port.TravelResearchAgentPort}.
 *
 * <p>When the ranking was already a typed empty result, narratives may be empty — the agent still
 * reports which prompt/model path produced (or skipped) the narrative half.
 */
public record TravelResearchNarratives(
        List<DestinationNarrative> narratives,
        String promptTemplateId,
        int promptVersion,
        String modelName) {

    public TravelResearchNarratives {
        Objects.requireNonNull(narratives, "narratives");
        Objects.requireNonNull(promptTemplateId, "promptTemplateId");
        Objects.requireNonNull(modelName, "modelName");
        narratives = List.copyOf(narratives);
        if (promptTemplateId.isBlank() || modelName.isBlank()) {
            throw new IllegalArgumentException("prompt/model labels must not be blank");
        }
        if (promptVersion < 0) {
            throw new IllegalArgumentException("promptVersion must not be negative");
        }
    }

    public Map<UUID, DestinationNarrative> byDestinationId() {
        return narratives.stream().collect(Collectors.toMap(
                DestinationNarrative::destinationId, Function.identity(), (a, b) -> a));
    }
}
