package com.travelplanner.domain.enums;

/** What kind of place a POI is (TRAVEL-KNOWLEDGE-CATALOG §2). */
public enum PoiCategory {

    /** Counted by the seed validator: ADR 010 §1 sets a floor of eight food POIs per destination. */
    FOOD,
    SIGHT,
    MUSEUM,
    NATURE,
    SHOPPING,
    NIGHTLIFE,
    EXPERIENCE,
    TRANSPORT_HUB
}
