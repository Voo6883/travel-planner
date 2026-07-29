package com.travelplanner.application.trip;

import java.util.UUID;

/**
 * {@code POST /api/v1/trips/{tripId}/actions/archive}.
 *
 * <p>A typed action rather than a status field on the update body (ADR 008 §3). Archiving is the
 * one transition out of the intake statuses this slice may perform, and it makes the trip read-only
 * for every actor — expressing it as "set this field to ARCHIVED" would put it in the same request
 * shape as a rename and lose that distinction at the exact place a client is most likely to send
 * one by accident.
 *
 * <p>It still carries {@code expectedVersion}: making a trip read-only while the agent is mid-edit
 * is a lost update like any other, and the loser has to be told rather than silently overruled.
 */
public record ArchiveTripCommand(UUID tripId, int expectedVersion) {
}
