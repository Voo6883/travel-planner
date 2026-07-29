package com.travelplanner.domain.enums;

/**
 * What the traveller wants out of the trip — the {@code interests} half of the C2 fit score
 * (PLAN §4.1.2: {@code w1·interest_match(poi_categories, brief.interests)}).
 *
 * <p><strong>A closed vocabulary, deliberately aligned with {@link PoiCategory}.</strong> Free text
 * would be friendlier to type and useless to rank: "temples and ramen" cannot be intersected with
 * anything the knowledge base stores, so the interest term of the score would silently contribute
 * zero and the user would be told their interests were considered when they were not. Every
 * constant here maps to exactly one POI category, so {@code interest_match} is a set intersection
 * with no lookup table between the two vocabularies and no interest that the corpus cannot answer.
 *
 * <p>{@link PoiCategory#TRANSPORT_HUB} has no counterpart on purpose. A station is something a trip
 * routes through, never something anybody travels for.
 */
public enum TravelInterest {

    FOOD(PoiCategory.FOOD),
    SIGHTSEEING(PoiCategory.SIGHT),
    MUSEUMS(PoiCategory.MUSEUM),
    NATURE(PoiCategory.NATURE),
    SHOPPING(PoiCategory.SHOPPING),
    NIGHTLIFE(PoiCategory.NIGHTLIFE),
    EXPERIENCES(PoiCategory.EXPERIENCE);

    private final PoiCategory poiCategory;

    TravelInterest(PoiCategory poiCategory) {
        this.poiCategory = poiCategory;
    }

    /** The knowledge-base category this interest is satisfied by. Never null. */
    public PoiCategory poiCategory() {
        return poiCategory;
    }
}
