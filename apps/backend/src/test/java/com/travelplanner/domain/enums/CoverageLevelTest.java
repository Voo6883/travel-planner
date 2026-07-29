package com.travelplanner.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class CoverageLevelTest {

    @Test
    void admitsOnlyFullyCuratedDestinationsToRanking() {
        assertThat(CoverageLevel.values())
                .filteredOn(CoverageLevel::isRankingEligible)
                .containsExactly(CoverageLevel.FULL);
    }

    @ParameterizedTest
    @EnumSource(value = CoverageLevel.class, names = {"PARTIAL", "NONE"})
    void refusesRankingForAnythingLessThanFullCoverage(CoverageLevel level) {
        // PARTIAL is the dangerous one: it has rows, so it would score above zero and read as a
        // considered-and-rejected destination rather than an unexamined one.
        assertThat(level.isRankingEligible()).isFalse();
    }
}
