package com.travelplanner.application.trip;

import java.util.UUID;

/**
 * {@code PUT /api/v1/trips/{tripId}} — the whole of what a client may write on the trip row.
 *
 * <p><strong>There is no {@code status} here, on purpose.</strong> {@code trip.status} is derived
 * from brief completeness by {@link TripBriefService}, and a client able to set it directly could
 * declare {@code BRIEF_COMPLETE} over an empty brief and walk straight past the C2 gate PLAN §3.1
 * depends on. The two status changes a client can actually ask for have their own typed entry
 * points: completing the brief (a consequence of saving it) and archiving
 * ({@code POST .../actions/archive}).
 */
public record RenameTripCommand(UUID tripId, int expectedVersion, String name) {
}
