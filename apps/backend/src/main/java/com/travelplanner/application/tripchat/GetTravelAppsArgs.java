package com.travelplanner.application.tripchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Set;

/** Validated {@code get_travel_apps} arguments (task 27, UC-C2-12). */
public record GetTravelAppsArgs(String countryCode, String inputJson) {

    private static final Set<String> ALLOWED = Set.of("country_code");

    public GetTravelAppsArgs {
        if (inputJson == null || inputJson.isBlank()) {
            inputJson = "{}";
        }
    }

    public static GetTravelAppsArgs parse(String toolName, String inputJson, ObjectMapper mapper) {
        if (!TripChatTools.GET_TRAVEL_APPS.equals(toolName)) {
            throw ToolArgsJson.failure("tool_name", "unknown trip tool: " + toolName);
        }
        JsonNode root = ToolArgsJson.readObject(inputJson, mapper);
        ToolArgsJson.rejectUnknownFields(root, ALLOWED);
        String code = ToolArgsJson.requireText(root, "country_code").trim().toUpperCase(Locale.ROOT);
        if (code.length() != 2) {
            throw ToolArgsJson.failure("country_code", "must be ISO alpha-2");
        }
        return new GetTravelAppsArgs(code, inputJson);
    }
}
