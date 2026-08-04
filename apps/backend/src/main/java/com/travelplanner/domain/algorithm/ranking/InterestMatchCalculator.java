package com.travelplanner.domain.algorithm.ranking;

import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.model.TripBriefDetails;
import java.util.List;

/** {@code w1·interest_match(poi_categories, brief.interests)} — set coverage in {@code [0, 1]}. */
final class InterestMatchCalculator {

    /** Neutral when the traveller stated no interests — not a perfect match, not a failure. */
    static final double NEUTRAL = 0.5d;

    private InterestMatchCalculator() {
    }

    static double score(DestinationCandidate candidate, TripBriefDetails brief) {
        List<TravelInterest> interests = brief.interests();
        if (interests.isEmpty()) {
            return NEUTRAL;
        }
        long matched = interests.stream()
                .filter(interest -> candidate.poiCategories().contains(interest.poiCategory()))
                .count();
        return matched / (double) interests.size();
    }
}
