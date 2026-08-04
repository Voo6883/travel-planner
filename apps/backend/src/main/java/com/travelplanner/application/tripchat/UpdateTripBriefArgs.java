package com.travelplanner.application.tripchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validated {@code update_trip_brief} tool arguments — the model-facing twin of
 * {@code SaveTripBriefCommand} (task 22, UC-C5-01).
 *
 * <p><strong>A merge, not a replace.</strong> Where the form's {@code PUT} reads an omitted field
 * as "clear it", the tool reads it as "leave it": the model is answering one part of a
 * conversation, not re-submitting the whole form, and clearing a field the traveller never
 * mentioned would silently lose data the form had saved. {@link #mergeOnto} therefore overwrites
 * only the fields that were present, and {@link #appliedFields()} names exactly those — which is
 * what the tool result tells the model it stored, so it cannot claim it saved a field it did not
 * send.
 *
 * <p>Every value is re-validated by the same value objects the form uses ({@link Money},
 * {@link DateRange}, {@link PartySize}, and {@code Enum.valueOf}), so a malformed budget or a
 * reversed date range is {@code validation_failed} here, exactly as it would be from the form — the
 * tool is never a second, weaker way to write the brief (task 22 "Do not: bypass validation").
 *
 * <p>Absent fields are {@code null} on this record; a present {@code surprise_me} is the one
 * boolean, so it is boxed to tell "the model said false" apart from "the model said nothing".
 */
public record UpdateTripBriefArgs(
        int expectedVersion,
        List<String> destinations,
        Boolean surpriseMe,
        DateRange dates,
        DateFlexibility dateFlexibility,
        String departureCity,
        Money budget,
        PartySize party,
        List<TravelInterest> interests,
        TravelPace pace,
        String inputJson) {

    private static final String EXPECTED_VERSION = "expected_version";
    private static final String DESTINATIONS = "destinations";
    private static final String SURPRISE_ME = "surprise_me";
    private static final String DATES = "dates";
    private static final String DATE_FLEXIBILITY = "date_flexibility";
    private static final String DEPARTURE_CITY = "departure_city";
    private static final String BUDGET = "budget";
    private static final String PARTY = "party";
    private static final String INTERESTS = "interests";
    private static final String PACE = "pace";

    private static final Set<String> ALLOWED = Set.of(EXPECTED_VERSION, DESTINATIONS, SURPRISE_ME,
            DATES, DATE_FLEXIBILITY, DEPARTURE_CITY, BUDGET, PARTY, INTERESTS, PACE);

    public UpdateTripBriefArgs {
        if (inputJson == null || inputJson.isBlank()) {
            inputJson = "{}";
        }
    }

    public static UpdateTripBriefArgs parse(String toolName, String inputJson, ObjectMapper mapper) {
        if (!TripChatTools.UPDATE_TRIP_BRIEF.equals(toolName)) {
            throw ToolArgsJson.failure("tool_name", "unknown trip tool: " + toolName);
        }
        JsonNode root = ToolArgsJson.readObject(inputJson, mapper);
        ToolArgsJson.rejectUnknownFields(root, ALLOWED);
        return new UpdateTripBriefArgs(
                ToolArgsJson.requireInt(root, EXPECTED_VERSION),
                ToolArgsJson.optionalTextList(root, DESTINATIONS),
                ToolArgsJson.optionalBoolean(root, SURPRISE_ME),
                dates(root),
                flexibility(root),
                ToolArgsJson.optionalText(root, DEPARTURE_CITY),
                budget(root),
                party(root),
                interests(root),
                pace(root),
                inputJson);
    }

    /** Overwrites only the fields this call carried, leaving everything else as {@code known} left it. */
    public TripBriefDetails mergeOnto(TripBriefDetails known) {
        TripBriefDetails merged = known;
        if (destinations != null) {
            merged = merged.withDestinations(destinations);
        }
        if (surpriseMe != null) {
            merged = merged.withSurpriseMe(surpriseMe);
        }
        if (dates != null) {
            merged = merged.withDates(dates);
        }
        if (dateFlexibility != null) {
            merged = merged.withDateFlexibility(dateFlexibility);
        }
        if (departureCity != null) {
            merged = merged.withDepartureCity(departureCity);
        }
        if (budget != null) {
            merged = merged.withBudget(budget);
        }
        if (party != null) {
            merged = merged.withParty(party);
        }
        if (interests != null) {
            merged = merged.withInterests(interests);
        }
        if (pace != null) {
            merged = merged.withPace(pace);
        }
        return merged;
    }

    /** The wire names of the fields this call actually set, for the tool result's applied summary. */
    public List<String> appliedFields() {
        List<String> applied = new ArrayList<>();
        addIf(applied, destinations != null, DESTINATIONS);
        addIf(applied, surpriseMe != null, SURPRISE_ME);
        addIf(applied, dates != null, DATES);
        addIf(applied, dateFlexibility != null, DATE_FLEXIBILITY);
        addIf(applied, departureCity != null, DEPARTURE_CITY);
        addIf(applied, budget != null, BUDGET);
        addIf(applied, party != null, PARTY);
        addIf(applied, interests != null, INTERESTS);
        addIf(applied, pace != null, PACE);
        return List.copyOf(applied);
    }

    private static void addIf(List<String> target, boolean condition, String field) {
        if (condition) {
            target.add(field);
        }
    }

    private static DateRange dates(JsonNode root) {
        JsonNode object = ToolArgsJson.optionalObject(root, DATES);
        return object == null ? null : ToolArgsJson.dateRangeOf(object, DATES);
    }

    private static DateFlexibility flexibility(JsonNode root) {
        String value = ToolArgsJson.optionalText(root, DATE_FLEXIBILITY);
        return value == null ? null : ToolArgsJson.enumValue(DateFlexibility.class, value, DATE_FLEXIBILITY);
    }

    private static Money budget(JsonNode root) {
        JsonNode object = ToolArgsJson.optionalObject(root, BUDGET);
        return object == null ? null : ToolArgsJson.moneyOf(object, BUDGET);
    }

    private static PartySize party(JsonNode root) {
        JsonNode object = ToolArgsJson.optionalObject(root, PARTY);
        if (object == null) {
            return null;
        }
        int adults = ToolArgsJson.requireInt(object, "adults");
        Integer children = ToolArgsJson.optionalInt(object, "children");
        return new PartySize(adults, children == null ? 0 : children);
    }

    private static List<TravelInterest> interests(JsonNode root) {
        List<String> values = ToolArgsJson.optionalTextList(root, INTERESTS);
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(value -> ToolArgsJson.enumValue(TravelInterest.class, value, INTERESTS))
                .toList();
    }

    private static TravelPace pace(JsonNode root) {
        String value = ToolArgsJson.optionalText(root, PACE);
        return value == null ? null : ToolArgsJson.enumValue(TravelPace.class, value, PACE);
    }
}
