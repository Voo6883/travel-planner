package com.travelplanner.application.tripchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Validated {@code get_destination_guide} arguments (task 27, UC-C2-12). */
public record GetDestinationGuideArgs(UUID destinationId, String locale, String inputJson) {

    private static final Set<String> ALLOWED = Set.of("destination_id", "locale");

    public GetDestinationGuideArgs {
        if (inputJson == null || inputJson.isBlank()) {
            inputJson = "{}";
        }
        if (locale == null || locale.isBlank()) {
            locale = "en";
        }
    }

    public static GetDestinationGuideArgs parse(
            String toolName, String inputJson, ObjectMapper mapper) {
        if (!TripChatTools.GET_DESTINATION_GUIDE.equals(toolName)) {
            throw ToolArgsJson.failure("tool_name", "unknown trip tool: " + toolName);
        }
        JsonNode root = ToolArgsJson.readObject(inputJson, mapper);
        ToolArgsJson.rejectUnknownFields(root, ALLOWED);
        UUID destinationId = uuid(ToolArgsJson.requireText(root, "destination_id"));
        String locale = ToolArgsJson.optionalText(root, "locale");
        if (locale != null) {
            locale = locale.trim().toLowerCase(Locale.ROOT);
            if (!"en".equals(locale) && !"ms".equals(locale)) {
                throw ToolArgsJson.failure("locale", "must be en or ms");
            }
        }
        return new GetDestinationGuideArgs(destinationId, locale, inputJson);
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException bad) {
            throw ToolArgsJson.failure("destination_id", "must be a UUID");
        }
    }
}
