package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.enums.CrowdBand;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.WeatherBand;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class SeasonalityMonthTest {

    private static final KnowledgeProvenance PROVENANCE = KnowledgeFixtures.provenance();

    @Test
    void answersAllThreeBandsForOneCalendarMonth() {
        // fitScore sums the three; a month answering only one of them scores as partially-known in
        // a total that cannot tell "mild" from "unrecorded".
        SeasonalityMonth april = new SeasonalityMonth(UUID.randomUUID(), UUID.randomUUID(), 4,
                WeatherBand.MILD, CrowdBand.PEAK, PriceBand.EXPENSIVE, "Cherry blossom season.",
                PROVENANCE);

        assertThat(april.month()).isEqualTo(4);
        assertThat(april.weatherBand()).isEqualTo(WeatherBand.MILD);
        assertThat(april.crowdBand()).isEqualTo(CrowdBand.PEAK);
        assertThat(april.priceBand()).isEqualTo(PriceBand.EXPENSIVE);
        assertThat(april.notesIfPresent()).contains("Cherry blossom season.");
        assertThat(april.provenance()).isEqualTo(PROVENANCE);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 13, -1, 12_000})
    void rejectsAMonthOutsideTheCalendar(int month) {
        // Zero is the one that matters: a zero-based month would shift a whole year of curation by
        // one column and still look like valid data in every row.
        assertThatThrownBy(() -> month(month))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("month must be 1..12");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 6, 12})
    void acceptsEveryMonthOfTheYearIncludingBothEnds(int month) {
        assertThat(month(month).month()).isEqualTo(month);
    }

    @Test
    void treatsAbsentCuratorNotesAsAbsentRatherThanEmpty() {
        assertThat(month(7).notesIfPresent()).isEmpty();
    }

    @Test
    void rejectsEveryRequiredFieldBeingAbsent() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new SeasonalityMonth(null, id, 1, WeatherBand.MILD,
                CrowdBand.LOW, PriceBand.BUDGET, null, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SeasonalityMonth(id, null, 1, WeatherBand.MILD,
                CrowdBand.LOW, PriceBand.BUDGET, null, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SeasonalityMonth(id, id, 1, null,
                CrowdBand.LOW, PriceBand.BUDGET, null, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SeasonalityMonth(id, id, 1, WeatherBand.MILD,
                null, PriceBand.BUDGET, null, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SeasonalityMonth(id, id, 1, WeatherBand.MILD,
                CrowdBand.LOW, null, null, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SeasonalityMonth(id, id, 1, WeatherBand.MILD,
                CrowdBand.LOW, PriceBand.BUDGET, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static SeasonalityMonth month(int month) {
        return new SeasonalityMonth(UUID.randomUUID(), UUID.randomUUID(), month,
                WeatherBand.MILD, CrowdBand.LOW, PriceBand.BUDGET, null, PROVENANCE);
    }
}
