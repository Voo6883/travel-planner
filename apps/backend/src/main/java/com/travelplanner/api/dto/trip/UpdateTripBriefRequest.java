package com.travelplanner.api.dto.trip;

import com.travelplanner.api.dto.VersionedMutation;
import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.model.TripBriefDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code PUT /api/v1/trips/{tripId}/brief} — the whole-body save the debounced intake form issues
 * (PLAN §4.2, ADR 008 §2).
 *
 * <p><strong>Every field except the version is optional, and an omitted field clears it.</strong>
 * This is a {@code PUT} of the whole resource, not a merge: {@code PATCH} is forbidden project-wide
 * (ADR 008 §3), and a {@code PUT} that treated absence as "leave unchanged" would give a client no
 * way at all to un-set a field it had entered by mistake. Clients that want to change one field
 * without re-sending the rest use the clarification action, which is the typed diff ADR 008 §3
 * provides for exactly that.
 *
 * <p>{@code expected_version} is {@code Integer} with {@code @NotNull}: a primitive would bind an
 * omitted value to {@code 0} and turn a forgetful client into a force-overwrite.
 */
public record UpdateTripBriefRequest(
        @NotNull @Min(0) Integer expectedVersion,

        @Size(max = TripBriefDetails.MAX_DESTINATIONS)
        List<String> destinations,

        @Valid DateRangePayload dates,

        DateFlexibility dateFlexibility,

        @Size(max = TripBriefDetails.MAX_DEPARTURE_CITY_LENGTH)
        String departureCity,

        @Valid MoneyPayload budget,

        @Valid PartySizePayload party,

        List<TravelInterest> interests,

        TravelPace pace) implements VersionedMutation {

    /**
     * The editable half of the brief, as one domain value.
     *
     * <p>Each conversion runs the value object's own invariants, so a reversed date range or a
     * negative budget fails here with the same {@code validation_failed} envelope an LLM tool
     * invocation would get for the same input (PLAN §4.1.3: one error vocabulary, two callers).
     */
    public TripBriefDetails toDetails() {
        return new TripBriefDetails(
                destinations,
                DateRangePayload.toDateRange(dates),
                dateFlexibility,
                departureCity,
                MoneyPayload.toMoney(budget),
                PartySizePayload.toPartySize(party),
                interests,
                pace);
    }
}
