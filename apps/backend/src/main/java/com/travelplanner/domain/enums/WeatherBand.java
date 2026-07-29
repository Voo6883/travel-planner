package com.travelplanner.domain.enums;

/**
 * A month's weather in the terms curated sources actually use.
 *
 * <p>Bands rather than temperatures: inventing a number to store would be exactly the fabrication
 * PLAN §4.1.0 forbids, and "hot and wet" is what a guide says.
 */
public enum WeatherBand {
    COLD,
    COOL,
    MILD,
    WARM,
    HOT,
    WET,
    STORMY
}
