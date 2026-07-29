package com.travelplanner.api.dto.trip;

import com.travelplanner.api.dto.VersionedMutation;
import com.travelplanner.domain.model.Trip;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/v1/trips/{tripId}} — ADR 008 §2.
 *
 * <p>{@code Integer} with {@code @NotNull}, never {@code int}: a primitive would bind an omitted
 * {@code expected_version} to {@code 0}, which is the version of a trip that has never been saved,
 * so a body that forgot the field would silently overwrite whatever the agent had just written.
 * ADR 008 requires a missing version to be {@code 400 validation_failed} and never a force
 * overwrite.
 *
 * <p>No {@code status}. The trip's status is server-derived; see {@code TripResponse}.
 */
public record RenameTripRequest(
        @NotNull @Min(0) Integer expectedVersion,
        @NotBlank @Size(max = Trip.MAX_NAME_LENGTH) String name) implements VersionedMutation {
}
