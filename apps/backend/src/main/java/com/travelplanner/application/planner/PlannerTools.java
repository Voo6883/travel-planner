package com.travelplanner.application.planner;

import com.travelplanner.domain.ai.ToolSpec;
import java.util.List;

/** Planner-surface tools offered to the model before a trip exists. */
public final class PlannerTools {

    public static final String CREATE_TRIP = "create_trip";

    private static final String CREATE_TRIP_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "name": {
                  "type": "string",
                  "maxLength": 120
                }
              }
            }""";

    private static final ToolSpec CREATE_TRIP_SPEC = new ToolSpec(
            CREATE_TRIP,
            "Create a draft trip once the traveller has given enough planning intent.",
            CREATE_TRIP_SCHEMA);

    private static final List<ToolSpec> SPECS = List.of(CREATE_TRIP_SPEC);

    private PlannerTools() {
    }

    public static List<ToolSpec> specs() {
        return SPECS;
    }

    public static boolean isCreateTrip(String toolName) {
        return CREATE_TRIP.equals(toolName);
    }
}
