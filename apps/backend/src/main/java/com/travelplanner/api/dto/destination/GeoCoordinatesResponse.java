package com.travelplanner.api.dto.destination;

import com.travelplanner.domain.model.Destination;

/**
 * A destination's map position, published as one object rather than two sibling fields.
 *
 * <p>{@link Destination} refuses a half-set coordinate because "latitude known, longitude null"
 * places a city on the Greenwich meridian and looks like data rather than like the mistake it is.
 * Nesting the pair carries that invariant onto the wire: a client reads {@code coordinates} as
 * present or absent, and never has to decide what a lone latitude means.
 *
 * @param latitude degrees north, -90..90
 * @param longitude degrees east, -180..180
 */
public record GeoCoordinatesResponse(double latitude, double longitude) {

    /** Null when the destination was curated without a position — the whole object is omitted. */
    public static GeoCoordinatesResponse from(Destination destination) {
        if (destination.latitude() == null || destination.longitude() == null) {
            return null;
        }
        return new GeoCoordinatesResponse(destination.latitude(), destination.longitude());
    }
}
