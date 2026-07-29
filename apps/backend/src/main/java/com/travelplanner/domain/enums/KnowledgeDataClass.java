package com.travelplanner.domain.enums;

import java.time.Duration;

/**
 * How quickly a kind of fact goes out of date (ADR 010 §6).
 *
 * <p>The TTLs differ by two orders of magnitude, and that spread is the point. A year-old
 * seasonality row is still broadly true; a year-old opening time is a wasted journey. ADR 010 puts
 * it plainly — "a stale opening-hours presented as fact is the most direct way to ruin a
 * traveller's day".
 *
 * <p>Expiry does not hide a row. Stale content stays retrievable and is returned with
 * {@code stale = true} so the agent can downgrade its confidence and say "as of last spring"
 * rather than asserting. Deleting it instead would replace a hedged answer with no answer.
 */
public enum KnowledgeDataClass {

    /** {@code poi.opening_hours} and {@code poi.price_band}. The shortest TTL, for the best reason. */
    POI_DETAILS(Duration.ofDays(90)),

    /** {@code travel_app} store URLs and availability — an app delisted in one market still 200s. */
    TRAVEL_APP(Duration.ofDays(180)),

    /** {@code price_history} and {@code seasonality} — refreshed by a job rather than flagged. */
    SEASONAL_PRICING(Duration.ofDays(365)),

    /** {@code destination_guide} narrative. Reviewed rather than refreshed. */
    GUIDE_NARRATIVE(Duration.ofDays(730));

    private final Duration timeToLive;

    KnowledgeDataClass(Duration timeToLive) {
        this.timeToLive = timeToLive;
    }

    public Duration timeToLive() {
        return timeToLive;
    }
}
