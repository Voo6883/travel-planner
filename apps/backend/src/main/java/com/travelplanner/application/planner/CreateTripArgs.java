package com.travelplanner.application.planner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.Trip;
import java.util.Iterator;
import java.util.Map;

/** Validated {@code create_trip} tool arguments. */
public record CreateTripArgs(String inputJson, String requestedName) {

    private static final String NAME = "name";

    public CreateTripArgs {
        if (inputJson == null || inputJson.isBlank()) {
            inputJson = "{}";
        }
    }

    public static CreateTripArgs parse(String toolName, String inputJson, ObjectMapper objectMapper) {
        if (!PlannerTools.isCreateTrip(toolName)) {
            throw failure("tool_name", "unknown planner tool: " + toolName);
        }
        JsonNode root = readObject(inputJson, objectMapper);
        rejectUnknownFields(root);
        return new CreateTripArgs(inputJson, name(root));
    }

    private static JsonNode readObject(String inputJson, ObjectMapper objectMapper) {
        try {
            JsonNode root = objectMapper.readTree(inputJson == null || inputJson.isBlank() ? "{}" : inputJson);
            if (root == null || !root.isObject()) {
                throw failure("tool_args", "must be a JSON object");
            }
            return root;
        } catch (JsonProcessingException failure) {
            throw failure("tool_args", "must be valid JSON");
        }
    }

    private static void rejectUnknownFields(JsonNode root) {
        Iterator<String> fields = root.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!NAME.equals(field)) {
                throw failure(field, "is not supported");
            }
        }
    }

    private static String name(JsonNode root) {
        JsonNode node = root.get(NAME);
        if (node == null) {
            return null;
        }
        if (!node.isTextual()) {
            throw failure(NAME, "must be a string");
        }
        if (node.textValue().length() > Trip.MAX_NAME_LENGTH) {
            throw failure(NAME, "must be at most " + Trip.MAX_NAME_LENGTH + " characters");
        }
        return node.textValue();
    }

    private static ValidationFailedException failure(String field, String message) {
        return new ValidationFailedException(Map.of(field, message));
    }
}
