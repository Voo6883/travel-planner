package com.travelplanner.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class DateRangeTest {

    private static final LocalDate APRIL_1 = LocalDate.of(2026, 4, 1);
    private static final LocalDate APRIL_5 = LocalDate.of(2026, 4, 5);

    @Test
    void rejectsAnEndBeforeItsStart() {
        assertThatThrownBy(() -> DateRange.of(APRIL_5, APRIL_1))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void rejectsNulls() {
        assertThatThrownBy(() -> DateRange.of(null, APRIL_5))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> DateRange.of(APRIL_1, null))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void countsDaysInclusivelyAndNightsExclusively() {
        DateRange range = DateRange.of(APRIL_1, APRIL_5);

        // 1st through 5th is five calendar days and four hotel nights. Conflating the two is the
        // classic off-by-one in every booking flow.
        assertThat(range.days()).isEqualTo(5);
        assertThat(range.nights()).isEqualTo(4);
    }

    @Test
    void aSingleDayRangeIsOneDayAndZeroNights() {
        DateRange sameDay = DateRange.singleDay(APRIL_1);

        assertThat(sameDay.days()).isEqualTo(1);
        assertThat(sameDay.nights()).isZero();
    }

    @Test
    void containsIsInclusiveOnBothEnds() {
        DateRange range = DateRange.of(APRIL_1, APRIL_5);

        assertThat(range.contains(APRIL_1)).isTrue();
        assertThat(range.contains(APRIL_5)).isTrue();
        assertThat(range.contains(APRIL_1.minusDays(1))).isFalse();
        assertThat(range.contains(APRIL_5.plusDays(1))).isFalse();
        assertThat(range.contains(null)).isFalse();
    }

    @Test
    void overlapsDetectsASingleSharedDay() {
        DateRange first = DateRange.of(APRIL_1, APRIL_5);
        DateRange touching = DateRange.of(APRIL_5, APRIL_5.plusDays(3));
        DateRange separate = DateRange.of(APRIL_5.plusDays(1), APRIL_5.plusDays(3));

        assertThat(first.overlaps(touching)).isTrue();
        assertThat(touching.overlaps(first)).isTrue();
        assertThat(first.overlaps(separate)).isFalse();
    }
}
