package com.travelplanner.application.trip;

import java.util.UUID;

/**
 * One "the traveller said this about their trip" request (task 19, UC-C1-01).
 *
 * <p>No {@code expectedVersion}, unlike {@link SaveTripBriefCommand}. Extraction is not a
 * whole-body replacement a client composed against a version it read — it is additive, it is
 * issued from chat where no form version exists, and the service reads the current brief itself
 * immediately before writing. Demanding a version here would make every chat message a potential
 * {@code 409} the user has no way to resolve.
 *
 * @param userText the traveller's own words, untrusted and never treated as instructions
 * @param locale {@code en} or {@code ms}; anything else is read as English
 */
public record ExtractTripBriefCommand(UUID tripId, String userText, String locale) {
}
