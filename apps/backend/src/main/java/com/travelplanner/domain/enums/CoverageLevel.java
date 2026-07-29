package com.travelplanner.domain.enums;

/**
 * How much of a destination the TKB actually holds (ADR 010 §4).
 *
 * <p>This exists so that "we have no data for Osaka" is a statement the system can make. Without
 * it, an unseeded destination scores near zero on three of {@code fitScore}'s four terms and simply
 * ranks low — indistinguishable from a place that was considered properly and found unsuitable.
 *
 * <p>Only {@link #FULL} enters C2 ranking. That rule lives on {@code Destination}, not in a query
 * filter, because a filter is easy to forget in the next query somebody writes.
 */
public enum CoverageLevel {

    /** Curated to the depth ADR 010 §1 requires: guide, ≥4 areas, ≥25 POIs, 12 months, apps. */
    FULL,

    /** Some rows exist, but not enough to rank honestly. Retrievable, never ranked. */
    PARTIAL,

    /** Known place, no curated content. */
    NONE;

    /** ADR 010 §4: only FULL destinations enter C2 ranking. */
    public boolean isRankingEligible() {
        return this == FULL;
    }
}
