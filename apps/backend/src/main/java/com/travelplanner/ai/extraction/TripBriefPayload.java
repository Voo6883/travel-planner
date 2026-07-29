package com.travelplanner.ai.extraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.travelplanner.domain.model.TripBriefDraft;
import java.util.List;
import java.util.Objects;

/**
 * The wire shape of {@link TripBriefExtractionPrompt#SCHEMA} — the model's answer, bound and not
 * yet believed.
 *
 * <p>It implements {@link TripBriefDraft} so that the validation lives in the domain
 * ({@code TripBriefExtraction.from}) while the Jackson annotations stay in {@code ai/}, which is
 * the only arrangement that satisfies both "domain is framework-free" and "the rule that decides
 * what a brief may hold is a domain rule". Nothing else in the application is allowed to see this
 * type; it exists for the length of one {@code extract} call.
 *
 * <p>{@code ignoreUnknown} is on deliberately. A model that adds a helpful {@code "notes"} key has
 * produced output this code can use in full, and failing it would spend a repair attempt — and
 * sometimes the whole call — on a deviation that cost nothing. Missing keys are equally fine:
 * absence is the normal case, because a traveller's first message rarely mentions seven fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TripBriefPayload(
        @JsonProperty("destinations") List<String> destinations,
        @JsonProperty("surprise_me") boolean surpriseMe,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("end_date") String endDate,
        @JsonProperty("date_flexibility") String dateFlexibility,
        @JsonProperty("departure_city") String departureCity,
        @JsonProperty("budget_amount") String budgetAmount,
        @JsonProperty("budget_currency") String budgetCurrency,
        @JsonProperty("adults") Integer adults,
        @JsonProperty("children") Integer children,
        @JsonProperty("interests") List<String> interests,
        @JsonProperty("pace") String pace,
        @JsonProperty("ambiguous_fields") List<String> ambiguousFields) implements TripBriefDraft {

    public TripBriefPayload {
        destinations = nonNulls(destinations);
        interests = nonNulls(interests);
        ambiguousFields = nonNulls(ambiguousFields);
    }

    /**
     * A {@code null} inside an array is model noise, not a value. {@code List.copyOf} would throw
     * on it and cost a repair attempt for a list that was otherwise perfectly usable.
     */
    private static List<String> nonNulls(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }
}
