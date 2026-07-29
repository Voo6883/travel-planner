package com.travelplanner.domain.enums;

/**
 * Which catalogue table a retrieval hit came from (ADR 010 §5).
 *
 * <p>Only the two embedded tables appear here. Everything else in the TKB is reached by key — a
 * seasonality row is looked up by month, not searched for — so a third constant would describe a
 * retrieval path that does not exist.
 */
public enum KnowledgeMatchType {

    /** One field group of a {@code destination_guide}, embedded separately per ADR 010 §5. */
    GUIDE,

    /** A whole {@code poi}: name, description and tags embed as a single chunk. */
    POI
}
