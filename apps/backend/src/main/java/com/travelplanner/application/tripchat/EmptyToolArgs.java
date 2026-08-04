package com.travelplanner.application.tripchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

/** Empty-object args for read-only research tools that take no parameters. */
public record EmptyToolArgs(String inputJson) {

    private static final Set<String> ALLOWED = Set.of();

    public EmptyToolArgs {
        if (inputJson == null || inputJson.isBlank()) {
            inputJson = "{}";
        }
    }

    public static EmptyToolArgs parse(String toolName, String inputJson, ObjectMapper mapper) {
        if (!TripChatTools.GET_RESEARCH_STATUS.equals(toolName)
                && !TripChatTools.GET_RECOMMENDATIONS_SUMMARY.equals(toolName)) {
            throw ToolArgsJson.failure("tool_name", "unknown trip tool: " + toolName);
        }
        JsonNode root = ToolArgsJson.readObject(inputJson, mapper);
        ToolArgsJson.rejectUnknownFields(root, ALLOWED);
        return new EmptyToolArgs(inputJson);
    }
}
