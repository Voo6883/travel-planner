package com.travelplanner.api.dto.trip;

import com.travelplanner.application.trip.TripBriefView;
import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.model.TripBrief;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The body of every brief read and of every successful brief mutation (ADR 008 §2: "every mutation
 * returns the new full resource with its incremented version").
 *
 * <p>Returning the whole resource rather than an acknowledgement is what makes the conflict rules
 * usable. After a save the client holds the exact state the server holds, including the new
 * {@code version} its next write must echo and the {@code clarification} the save recomputed — so
 * the debounced form's next keystroke does not race a refetch it would otherwise have to make.
 *
 * <p>{@code status} is the <em>trip's</em> status, carried here because it changes as a consequence
 * of saving the brief. A client that had to fetch it separately would, in the gap, render a
 * "start research" button for a brief the agent had just re-opened.
 *
 * <p>{@code version} is the <em>brief's</em>. The two aggregates version independently, and
 * {@code expected_version} on this endpoint means this field.
 */
public record TripBriefResponse(
        UUID tripId,
        TripStatus status,
        List<String> destinations,
        DateRangePayload dates,
        DateFlexibility dateFlexibility,
        String departureCity,
        MoneyPayload budget,
        PartySizePayload party,
        List<TravelInterest> interests,
        TravelPace pace,
        ClarificationResponse clarification,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public static TripBriefResponse from(TripBriefView view) {
        TripBrief brief = view.brief();
        return new TripBriefResponse(
                brief.tripId(),
                view.status(),
                brief.destinations(),
                DateRangePayload.from(brief.dates()),
                brief.dateFlexibility(),
                brief.departureCity(),
                MoneyPayload.from(brief.budget()),
                PartySizePayload.from(brief.party()),
                brief.interests(),
                brief.pace(),
                ClarificationResponse.from(view.clarification()),
                brief.version(),
                brief.createdAt(),
                brief.updatedAt());
    }
}
