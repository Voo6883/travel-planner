package com.travelplanner.api.dto.trip;

import com.travelplanner.domain.valueobject.PartySize;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * How many people are travelling, on the wire.
 *
 * <p>{@code Integer} and {@code @NotNull} rather than {@code int}. A primitive would bind an
 * omitted {@code adults} to {@code 0}, and a party of zero adults is a brief that would then be
 * researched and priced for nobody — the same "absent must never become a default" rule ADR 008 §2
 * applies to {@code expected_version}.
 *
 * <p>The upper bound lives in {@link PartySize}, not here: it is a domain fact about what C4
 * suppliers will quote as one party, and repeating it in an annotation would give two places for it
 * to be changed in.
 */
public record PartySizePayload(
        @NotNull @Min(PartySize.MIN_ADULTS) Integer adults,
        @NotNull @Min(0) Integer children) {

    /** @return null when the brief has no party yet */
    public static PartySizePayload from(PartySize party) {
        return party == null ? null : new PartySizePayload(party.adults(), party.children());
    }

    /** @throws com.travelplanner.domain.exception.ValidationFailedException on an invalid party */
    public PartySize toPartySize() {
        return new PartySize(adults, children);
    }

    /** Null-tolerant, so an omitted party does not need a branch at every call site. */
    public static PartySize toPartySize(PartySizePayload payload) {
        return payload == null ? null : payload.toPartySize();
    }
}
