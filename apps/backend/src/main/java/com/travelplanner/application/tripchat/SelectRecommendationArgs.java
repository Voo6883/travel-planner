package com.travelplanner.application.tripchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Validated {@code select_recommendation} arguments (task 27, UC-C5-05).
 *
 * <p>{@code confirmation=explicit} requires {@code recommendation_id}. {@code just_pick} selects
 * the highest-ranked recommendation and ignores any id (PLAN §3.2 "just pick for me").
 */
public record SelectRecommendationArgs(
        Confirmation confirmation, UUID recommendationId, String inputJson) {

    public enum Confirmation {
        EXPLICIT,
        JUST_PICK
    }

    private static final Set<String> ALLOWED = Set.of("confirmation", "recommendation_id");

    public SelectRecommendationArgs {
        if (inputJson == null || inputJson.isBlank()) {
            inputJson = "{}";
        }
        if (confirmation == Confirmation.EXPLICIT && recommendationId == null) {
            throw ToolArgsJson.failure("recommendation_id", "is required for explicit confirmation");
        }
    }

    public static SelectRecommendationArgs parse(
            String toolName, String inputJson, ObjectMapper mapper) {
        if (!TripChatTools.SELECT_RECOMMENDATION.equals(toolName)) {
            throw ToolArgsJson.failure("tool_name", "unknown trip tool: " + toolName);
        }
        JsonNode root = ToolArgsJson.readObject(inputJson, mapper);
        ToolArgsJson.rejectUnknownFields(root, ALLOWED);
        String raw = ToolArgsJson.requireText(root, "confirmation");
        Confirmation mode = switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "explicit" -> Confirmation.EXPLICIT;
            case "just_pick" -> Confirmation.JUST_PICK;
            default -> throw ToolArgsJson.failure("confirmation",
                    "'" + raw + "' is not a valid option");
        };
        UUID recommendationId = null;
        if (ToolArgsJson.present(root, "recommendation_id")) {
            recommendationId = uuid(ToolArgsJson.requireText(root, "recommendation_id"));
        }
        return new SelectRecommendationArgs(mode, recommendationId, inputJson);
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException bad) {
            throw ToolArgsJson.failure("recommendation_id", "must be a UUID");
        }
    }
}
