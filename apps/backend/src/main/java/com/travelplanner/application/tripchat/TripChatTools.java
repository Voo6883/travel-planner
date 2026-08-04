package com.travelplanner.application.tripchat;

import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.enums.TripStatus;
import java.util.List;

/**
 * The trip-surface tools offered to the model once a trip exists, gated by {@code trip.status}.
 *
 * <p>The gate is the point (task 22 Scope). {@code update_trip_brief} is only ever offered while
 * the brief is still being built — {@code DRAFT} or {@code CLARIFICATION_NEEDED} — and
 * {@code answer_clarification} only while there are questions outstanding, which is exactly
 * {@code CLARIFICATION_NEEDED}. A trip that has left intake ({@code BRIEF_COMPLETE},
 * {@code ARCHIVED}, or anything in C2+) is offered nothing, so the model cannot be tempted to edit
 * a brief the server would refuse anyway. {@link TripChatToolService} re-checks the same rule
 * server-side; offering the empty list here is what stops the model asking in the first place.
 *
 * <p>Every property name is {@code snake_case} to match the OpenAPI wire style
 * ({@code UpdateTripBriefRequest}, {@code ClarificationAnswer}), and every object sets
 * {@code additionalProperties: false} so a model that invents a field is refused at parse time
 * rather than having it silently ignored. The schema is the same declaration the model reads and
 * the orchestrator validates against — one source, so the two cannot disagree (ADR 007,
 * {@code docs/AGENT-HARNESS.md} §4).
 *
 * <h2>Adding a tool (task 23/27)</h2>
 *
 * <p>Declare its schema and {@link ToolSpec} here, then widen {@link #specsFor(TripStatus)} to name
 * the statuses it is legal in — and nowhere else. The status gate and the offered set are the same
 * method, so a tool that is not in {@code specsFor} for a status can never be offered in it.
 */
public final class TripChatTools {

    public static final String UPDATE_TRIP_BRIEF = "update_trip_brief";
    public static final String ANSWER_CLARIFICATION = "answer_clarification";

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

    private TripChatTools() {
    }

    /**
     * The tools legal in {@code status}, in the order they are offered.
     *
     * <p>The empty default is not a gap: {@code BRIEF_COMPLETE} and every C2+ status is a trip whose
     * brief the intake tools may not touch, and offering nothing is how that is enforced before the
     * model can act.
     */
    public static List<ToolSpec> specsFor(TripStatus status) {
        return switch (status) {
            case DRAFT -> List.of(UPDATE_TRIP_BRIEF_SPEC);
            case CLARIFICATION_NEEDED -> List.of(UPDATE_TRIP_BRIEF_SPEC, ANSWER_CLARIFICATION_SPEC);
            default -> List.of();
        };
    }

    /** True when {@code toolName} is a tool this registry may offer in {@code status}. */
    public static boolean isAllowed(TripStatus status, String toolName) {
        return specsFor(status).stream().anyMatch(spec -> spec.name().equals(toolName));
    }

    /** True when {@code toolName} is one of the trip-surface tools, in any status. */
    public static boolean isKnown(String toolName) {
        return UPDATE_TRIP_BRIEF.equals(toolName) || ANSWER_CLARIFICATION.equals(toolName);
    }
}
