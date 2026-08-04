package com.travelplanner.application.tripchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

/**
 * Validated {@code start_research} arguments (task 27, UC-C5-03).
 *
 * <p>{@code user_confirmed} must be {@code true}. The model may only set that after the traveller
 * agreed, or when they clearly asked to research now (PLAN §3.2 decision policy).
 */
public record StartResearchArgs(boolean userConfirmed, String inputJson) {

    private static final Set<String> ALLOWED = Set.of("user_confirmed");

    public StartResearchArgs {
        if (inputJson == null || inputJson.isBlank()) {
            inputJson = "{}";
        }
    }

    public static StartResearchArgs parse(String toolName, String inputJson, ObjectMapper mapper) {
        if (!TripChatTools.START_RESEARCH.equals(toolName)) {
            throw ToolArgsJson.failure("tool_name", "unknown trip tool: " + toolName);
        }
        JsonNode root = ToolArgsJson.readObject(inputJson, mapper);
        ToolArgsJson.rejectUnknownFields(root, ALLOWED);
        Boolean confirmed = ToolArgsJson.optionalBoolean(root, "user_confirmed");
        if (confirmed == null) {
            throw ToolArgsJson.failure("user_confirmed", "is required");
        }
        if (!confirmed) {
            throw ToolArgsJson.failure("user_confirmed",
                    "must be true — ask the traveller before starting research");
        }
        return new StartResearchArgs(true, inputJson);
    }
}
