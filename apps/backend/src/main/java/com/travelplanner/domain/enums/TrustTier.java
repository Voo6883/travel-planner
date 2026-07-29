package com.travelplanner.domain.enums;

/** How much weight a fact's origin carries (ADR 010 §2). */
public enum TrustTier {

    /** A tourism board or operator publishing its own hours and fares. */
    OFFICIAL,

    /** Wikivoyage, OpenStreetMap: broad coverage, variable currency. */
    COMMUNITY,

    /**
     * The reserved tier for sample rows. ADR 010 §3 requires these to be visibly flagged and never
     * to look real, so nothing in this tier may be presented as a curated fact.
     */
    SAMPLE
}
