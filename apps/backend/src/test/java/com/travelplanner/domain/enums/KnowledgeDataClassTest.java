package com.travelplanner.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class KnowledgeDataClassTest {

    @ParameterizedTest
    @CsvSource({
        "POI_DETAILS, 90",
        "TRAVEL_APP, 180",
        "SEASONAL_PRICING, 365",
        "GUIDE_NARRATIVE, 730",
    })
    void carriesTheTimeToLiveAdr010Section6Fixes(KnowledgeDataClass dataClass, long expectedDays) {
        assertThat(dataClass.timeToLive()).isEqualTo(Duration.ofDays(expectedDays));
    }

    @Test
    void expiresPoiDetailsSoonestAndGuideNarrativeLatest() {
        // The spread is the point of the enum: a year-old seasonality row is still broadly true,
        // a year-old opening time is a wasted journey.
        assertThat(KnowledgeDataClass.POI_DETAILS.timeToLive())
                .isLessThan(KnowledgeDataClass.TRAVEL_APP.timeToLive());
        assertThat(KnowledgeDataClass.GUIDE_NARRATIVE.timeToLive())
                .isGreaterThan(KnowledgeDataClass.SEASONAL_PRICING.timeToLive());
    }

    @Test
    void givesEveryDataClassAPositiveTimeToLive() {
        // A zero or negative TTL would mark every row stale the instant it was written, which is
        // indistinguishable in the UI from having no data at all.
        assertThat(KnowledgeDataClass.values())
                .allSatisfy(dataClass -> assertThat(dataClass.timeToLive()).isPositive());
    }
}
