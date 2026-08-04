package com.travelplanner.application.tripchat;

import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.enums.TripStatus;
import java.util.List;

/**
 * Trip-surface tools gated by {@code trip.status} (tasks 22 and 27).
 *
 * <p>Intake tools ({@code update_trip_brief}, {@code answer_clarification}) stay on DRAFT /
 * CLARIFICATION_NEEDED. Research tools unlock from {@code BRIEF_COMPLETE} onward per PLAN §3.2.
 * {@link TripChatToolService} / {@link TripChatResearchToolService} re-check the same gate
 * server-side before any write.
 */
public final class TripChatTools {

    public static final String UPDATE_TRIP_BRIEF = "update_trip_brief";
    public static final String ANSWER_CLARIFICATION = "answer_clarification";
    public static final String START_RESEARCH = "start_research";
    public static final String GET_RESEARCH_STATUS = "get_research_status";
    public static final String GET_RECOMMENDATIONS_SUMMARY = "get_recommendations_summary";
    public static final String SELECT_RECOMMENDATION = "select_recommendation";
    public static final String GET_DESTINATION_GUIDE = "get_destination_guide";
    public static final String GET_TRAVEL_APPS = "get_travel_apps";

    private static final String UPDATE_TRIP_BRIEF_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["expected_version"],
              "properties": {
                "expected_version": { "type": "integer", "minimum": 0 },
                "destinations": {
                  "type": "array",
                  "maxItems": 10,
                  "items": { "type": "string" }
                },
                "surprise_me": { "type": "boolean" },
                "dates": {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["start_date", "end_date"],
                  "properties": {
                    "start_date": { "type": "string", "format": "date" },
                    "end_date": { "type": "string", "format": "date" }
                  }
                },
                "date_flexibility": {
                  "type": "string",
                  "enum": ["FIXED", "FLEXIBLE_WEEK", "FLEXIBLE_MONTH"]
                },
                "departure_city": { "type": "string", "maxLength": 120 },
                "budget": {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["amount", "currency"],
                  "properties": {
                    "amount": { "type": "string" },
                    "currency": { "type": "string" }
                  }
                },
                "party": {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["adults"],
                  "properties": {
                    "adults": { "type": "integer", "minimum": 1 },
                    "children": { "type": "integer", "minimum": 0 }
                  }
                },
                "interests": {
                  "type": "array",
                  "items": {
                    "type": "string",
                    "enum": ["FOOD", "SIGHTSEEING", "MUSEUMS", "NATURE",
                             "SHOPPING", "NIGHTLIFE", "EXPERIENCES"]
                  }
                },
                "pace": { "type": "string", "enum": ["RELAXED", "MODERATE", "PACKED"] }
              }
            }""";

    private static final String ANSWER_CLARIFICATION_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["expected_version", "answers"],
              "properties": {
                "expected_version": { "type": "integer", "minimum": 0 },
                "answers": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "object",
                    "additionalProperties": false,
                    "required": ["question_id"],
                    "properties": {
                      "question_id": { "type": "string" },
                      "text": { "type": "string" },
                      "number": { "type": "integer" },
                      "money": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["amount", "currency"],
                        "properties": {
                          "amount": { "type": "string" },
                          "currency": { "type": "string" }
                        }
                      },
                      "date_range": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["start_date", "end_date"],
                        "properties": {
                          "start_date": { "type": "string", "format": "date" },
                          "end_date": { "type": "string", "format": "date" }
                        }
                      },
                      "choice": { "type": "string" },
                      "choices": { "type": "array", "items": { "type": "string" } }
                    }
                  }
                }
              }
            }""";

    private static final String START_RESEARCH_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["user_confirmed"],
              "properties": {
                "user_confirmed": {
                  "type": "boolean",
                  "description": "True only after the traveller agreed to start research, or when \
they clearly asked to research now."
                }
              }
            }""";

    private static final String EMPTY_OBJECT_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {}
            }""";

    private static final String SELECT_RECOMMENDATION_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["confirmation"],
              "properties": {
                "confirmation": {
                  "type": "string",
                  "enum": ["explicit", "just_pick"],
                  "description": "explicit = traveller named/confirmed a pick; just_pick = they \
said just pick for me."
                },
                "recommendation_id": {
                  "type": "string",
                  "format": "uuid",
                  "description": "Required for explicit; ignored for just_pick (highest rank)."
                }
              }
            }""";

    private static final String GET_DESTINATION_GUIDE_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["destination_id"],
              "properties": {
                "destination_id": { "type": "string", "format": "uuid" },
                "locale": { "type": "string", "enum": ["en", "ms"] }
              }
            }""";

    private static final String GET_TRAVEL_APPS_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["country_code"],
              "properties": {
                "country_code": { "type": "string", "minLength": 2, "maxLength": 2 }
              }
            }""";

    private static final ToolSpec UPDATE_TRIP_BRIEF_SPEC = new ToolSpec(
            UPDATE_TRIP_BRIEF,
            "Save validated trip-brief fields the traveller has stated. Send expected_version and "
                    + "only the fields they gave; the server re-validates and reports what it stored.",
            UPDATE_TRIP_BRIEF_SCHEMA);

    private static final ToolSpec ANSWER_CLARIFICATION_SPEC = new ToolSpec(
            ANSWER_CLARIFICATION,
            "Answer one or more outstanding clarification questions with the value type each asked "
                    + "for. Send expected_version and the question_id of each answer.",
            ANSWER_CLARIFICATION_SCHEMA);

    private static final ToolSpec START_RESEARCH_SPEC = new ToolSpec(
            START_RESEARCH,
            "Start destination research when the brief is complete and the traveller confirmed "
                    + "(or clearly asked to research now). Set user_confirmed true only then.",
            START_RESEARCH_SCHEMA);

    private static final ToolSpec GET_RESEARCH_STATUS_SPEC = new ToolSpec(
            GET_RESEARCH_STATUS,
            "Read the persisted research job status and advisory progress_pct. Never invent "
                    + "percentages or claim research is complete unless status is completed.",
            EMPTY_OBJECT_SCHEMA);

    private static final ToolSpec GET_RECOMMENDATIONS_SUMMARY_SPEC = new ToolSpec(
            GET_RECOMMENDATIONS_SUMMARY,
            "Summarise persisted ranked recommendations (or the typed no-confident-result). "
                    + "Ground every claim in the returned fields and source_refs.",
            EMPTY_OBJECT_SCHEMA);

    private static final ToolSpec SELECT_RECOMMENDATION_SPEC = new ToolSpec(
            SELECT_RECOMMENDATION,
            "Select a destination after RESEARCH_READY. Use confirmation=explicit with "
                    + "recommendation_id after the traveller confirms, or just_pick when they said "
                    + "just pick for me (highest-ranked).",
            SELECT_RECOMMENDATION_SCHEMA);

    private static final ToolSpec GET_DESTINATION_GUIDE_SPEC = new ToolSpec(
            GET_DESTINATION_GUIDE,
            "Fetch the knowledge-base destination guide (areas, food, sights, mobility, apps).",
            GET_DESTINATION_GUIDE_SCHEMA);

    private static final ToolSpec GET_TRAVEL_APPS_SPEC = new ToolSpec(
            GET_TRAVEL_APPS,
            "Fetch the locale travel-app pack for a country (ride, maps, pay, food, …).",
            GET_TRAVEL_APPS_SCHEMA);

    private TripChatTools() {
    }

    /**
     * Tools legal in {@code status}, in offer order.
     */
    public static List<ToolSpec> specsFor(TripStatus status) {
        return switch (status) {
            case DRAFT -> List.of(UPDATE_TRIP_BRIEF_SPEC);
            case CLARIFICATION_NEEDED -> List.of(UPDATE_TRIP_BRIEF_SPEC, ANSWER_CLARIFICATION_SPEC);
            case BRIEF_COMPLETE -> List.of(START_RESEARCH_SPEC);
            case RESEARCH_QUEUED, RESEARCH_RUNNING -> List.of(GET_RESEARCH_STATUS_SPEC);
            case RESEARCH_READY -> List.of(
                    GET_RESEARCH_STATUS_SPEC,
                    GET_RECOMMENDATIONS_SUMMARY_SPEC,
                    SELECT_RECOMMENDATION_SPEC,
                    GET_DESTINATION_GUIDE_SPEC,
                    GET_TRAVEL_APPS_SPEC);
            case DESTINATION_SELECTED -> List.of(
                    GET_RESEARCH_STATUS_SPEC,
                    GET_RECOMMENDATIONS_SUMMARY_SPEC,
                    GET_DESTINATION_GUIDE_SPEC,
                    GET_TRAVEL_APPS_SPEC);
            default -> List.of();
        };
    }

    /** True when {@code toolName} is a tool this registry may offer in {@code status}. */
    public static boolean isAllowed(TripStatus status, String toolName) {
        return specsFor(status).stream().anyMatch(spec -> spec.name().equals(toolName));
    }

    /** True when {@code toolName} is one of the trip-surface tools, in any status. */
    public static boolean isKnown(String toolName) {
        return UPDATE_TRIP_BRIEF.equals(toolName)
                || ANSWER_CLARIFICATION.equals(toolName)
                || START_RESEARCH.equals(toolName)
                || GET_RESEARCH_STATUS.equals(toolName)
                || GET_RECOMMENDATIONS_SUMMARY.equals(toolName)
                || SELECT_RECOMMENDATION.equals(toolName)
                || GET_DESTINATION_GUIDE.equals(toolName)
                || GET_TRAVEL_APPS.equals(toolName);
    }

    /** True when the tool mutates trip/research state (emits a domain event after commit). */
    public static boolean isMutating(String toolName) {
        return START_RESEARCH.equals(toolName)
                || SELECT_RECOMMENDATION.equals(toolName)
                || UPDATE_TRIP_BRIEF.equals(toolName)
                || ANSWER_CLARIFICATION.equals(toolName);
    }
}
