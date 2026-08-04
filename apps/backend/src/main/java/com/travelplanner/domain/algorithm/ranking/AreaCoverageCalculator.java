package com.travelplanner.domain.algorithm.ranking;

import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.valueobject.PartySize;

/** {@code w4·area_coverage(destination_area, brief.party_size + pace)}. */
final class AreaCoverageCalculator {

    private AreaCoverageCalculator() {
    }

    static double score(DestinationCandidate candidate, TripBriefDetails brief) {
        int needed = areasNeeded(brief);
        if (needed <= 0) {
            return 1.0d;
        }
        return Math.min(1.0d, candidate.areaCount() / (double) needed);
    }

    private static int areasNeeded(TripBriefDetails brief) {
        PartySize party = brief.party() == null ? PartySize.ofAdults(2) : brief.party();
        TravelPace pace = brief.pace() == null ? TravelPace.MODERATE : brief.pace();
        int total = party.total();
        return switch (pace) {
            case RELAXED -> Math.max(1, total / 4);
            case MODERATE -> Math.max(2, (total + 2) / 3);
            case PACKED -> Math.max(3, (total + 1) / 2);
        };
    }
}
