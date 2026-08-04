package com.travelplanner.application.planner;

/** Converts validated tool input into a safe user-visible trip name. */
public final class TripNamer {

    private static final String FALLBACK_NAME = "New trip";

    private TripNamer() {
    }

    public static String nameFor(CreateTripArgs args) {
        String requested = args.requestedName();
        if (requested == null) {
            return FALLBACK_NAME;
        }
        String trimmed = requested.trim();
        return trimmed.isEmpty() ? FALLBACK_NAME : trimmed;
    }
}
