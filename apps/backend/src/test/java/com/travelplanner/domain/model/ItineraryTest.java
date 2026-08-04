package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.ItineraryItemCategory;
import com.travelplanner.domain.enums.ItineraryStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The aggregate's invariants — chiefly task 28's "invalid schedules cannot be persisted as ready".
 *
 * <p>The rule cannot be a database CHECK: it spans three tables and asks questions no single row can
 * answer. So it lives in the constructor every write path has to go through, and these are the
 * assertions that keep it there.
 */
class ItineraryTest {

    private static final LocalDate START = LocalDate.of(2026, 4, 1);

    @Test
    void carriesThePlanAndItsZone() {
        Itinerary itinerary = itinerary(ItineraryStatus.READY, START, START, List.of(day(1, START)));

        assertThat(itinerary.isPublishable()).isTrue();
        assertThat(itinerary.zone().getId()).isEqualTo("Asia/Tokyo");
        assertThat(itinerary.day(1)).isPresent();
        assertThat(itinerary.day(2)).isEmpty();
    }

    // ------------------------------------------------------------------- the READY rule (DoD)

    @Test
    void refusesToBeReadyWithNoDaysAtAll() {
        assertThatThrownBy(() -> itinerary(ItineraryStatus.READY, START, START, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have at least one day");
    }

    /**
     * An empty day in a published plan reads as "we had nothing for you" dressed as a finished
     * itinerary — the traveller opens day 2 to a blank page.
     */
    @Test
    void refusesToBeReadyWithAnEmptyDay() {
        assertThatThrownBy(() -> itinerary(ItineraryStatus.READY, START, START.plusDays(1),
                List.of(day(1, START), emptyDay(2, START.plusDays(1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("day 2 is empty");
    }

    /** A half-built plan that cannot be saved cannot be resumed, so DRAFT is held to none of it. */
    @Test
    void allowsADraftToBeIncompleteWhileItIsBeingAssembled() {
        assertThatCode(() -> itinerary(ItineraryStatus.DRAFT, START, START, List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> itinerary(ItineraryStatus.DRAFT, START, START.plusDays(1),
                List.of(day(1, START), emptyDay(2, START.plusDays(1)))))
                .doesNotThrowAnyException();
    }

    /** Promotion applies the rule rather than trusting the caller. */
    @Test
    void refusesToPromoteADraftThatIsNotPublishable() {
        Itinerary draft = itinerary(ItineraryStatus.DRAFT, START, START.plusDays(1),
                List.of(day(1, START), emptyDay(2, START.plusDays(1))));

        assertThatThrownBy(draft::markReady)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("day 2 is empty");
    }

    @Test
    void promotesACompleteDraft() {
        Itinerary draft = itinerary(ItineraryStatus.DRAFT, START, START, List.of(day(1, START)));

        assertThat(draft.markReady().isPublishable()).isTrue();
    }

    // ------------------------------------------------------------------------------ day numbering

    @Test
    void refusesAGapInTheDayNumbers() {
        assertThatThrownBy(() -> itinerary(ItineraryStatus.DRAFT, START, START.plusDays(2),
                List.of(day(1, START), day(3, START.plusDays(2)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without gaps");
    }

    /** Catches a plan built by arithmetic that assumed every month has 30 days. */
    @Test
    void refusesADayWhoseDateDoesNotMatchItsOrdinal() {
        assertThatThrownBy(() -> itinerary(ItineraryStatus.DRAFT, START, START.plusDays(1),
                List.of(day(1, START), day(2, START.plusDays(9)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("should fall on 2026-04-02");
    }

    @Test
    void refusesMoreDaysThanTheDateRangeHolds() {
        assertThatThrownBy(() -> itinerary(ItineraryStatus.DRAFT, START, START,
                List.of(day(1, START), day(2, START.plusDays(1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 days for a 1-day range");
    }

    @Test
    void refusesAnEndDateBeforeItsStart() {
        assertThatThrownBy(() -> itinerary(ItineraryStatus.DRAFT, START, START.minusDays(1),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endDate must not precede startDate");
    }

    // ---------------------------------------------------------------------------------- timezone

    /**
     * The copy is validated for the same reason {@code Destination} validates the original: a zone
     * the JVM cannot resolve fails later, in the renderer, naming the wrong culprit. {@code UTC+9}
     * is a fixed offset that ignores DST, not a zone.
     */
    @ParameterizedTest
    @ValueSource(strings = {"Tokio", "Asia/Tokio", "UTC+9", "GMT+09:00", "", "   "})
    void refusesATimezoneThatIsNotAResolvableIanaZone(String timezone) {
        assertThatThrownBy(() -> new Itinerary(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), ItineraryStatus.DRAFT, START, START,
                timezone, "v1", List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IANA zone");
    }

    // --------------------------------------------------------------------------------- day rules

    /** The overlap invariant is asserted where the day is built, not at the boundary. */
    @Test
    void refusesTwoBlocksThatShareAMinute() {
        assertThatThrownBy(() -> new ItineraryDay(UUID.randomUUID(), 1, START, null,
                LocalTime.of(9, 0), LocalTime.of(20, 0),
                List.of(item("A", LocalTime.of(9, 0), LocalTime.of(11, 0)),
                        item("B", LocalTime.of(10, 30), LocalTime.of(12, 0)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    void acceptsBlocksThatMerelyTouch() {
        assertThatCode(() -> new ItineraryDay(UUID.randomUUID(), 1, START, null,
                LocalTime.of(9, 0), LocalTime.of(20, 0),
                List.of(item("A", LocalTime.of(9, 0), LocalTime.of(11, 0)),
                        item("B", LocalTime.of(11, 0), LocalTime.of(12, 0)))))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesABlockOutsideTheDayWindow() {
        assertThatThrownBy(() -> new ItineraryDay(UUID.randomUUID(), 1, START, null,
                LocalTime.of(9, 0), LocalTime.of(12, 0),
                List.of(item("Late", LocalTime.of(11, 0), LocalTime.of(13, 0)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the day window");
    }

    /** Held in clock order regardless of the order they arrive in. */
    @Test
    void ordersItemsByStartTimeWhateverOrderTheyArriveIn() {
        ItineraryDay day = new ItineraryDay(UUID.randomUUID(), 1, START, null,
                LocalTime.of(9, 0), LocalTime.of(20, 0),
                List.of(item("Second", LocalTime.of(14, 0), LocalTime.of(15, 0)),
                        item("First", LocalTime.of(9, 0), LocalTime.of(10, 0))));

        assertThat(day.items()).extracting(ItineraryItem::title).containsExactly("First", "Second");
    }

    // ------------------------------------------------------------------------------- item rules

    /** UC-C3-03: a block asserting a place with nothing behind it is an ungrounded claim. */
    @Test
    void refusesASightWithNoPoiBehindIt() {
        assertThatThrownBy(() -> new ItineraryItem(UUID.randomUUID(), 0, null,
                ItineraryItemCategory.SIGHT, "Nowhere", LocalTime.of(9, 0), LocalTime.of(10, 0),
                60, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grounded in a POI");
    }

    /** A meal slot may legitimately be held open with nothing to fill it (UC-C3-06). */
    @Test
    void allowsAnUnfilledMealSlotToCarryNoPoi() {
        assertThatCode(() -> new ItineraryItem(UUID.randomUUID(), 0, null,
                ItineraryItemCategory.FOOD, "Lunch", LocalTime.of(12, 0), LocalTime.of(13, 0),
                60, null, null))
                .doesNotThrowAnyException();
    }

    /** The two facts come from different places, so a disagreement is a defect worth failing on. */
    @Test
    void refusesADurationThatDisagreesWithTheScheduledWindow() {
        assertThatThrownBy(() -> new ItineraryItem(UUID.randomUUID(), 0, UUID.randomUUID(),
                ItineraryItemCategory.SIGHT, "Senso-ji", LocalTime.of(9, 0), LocalTime.of(10, 0),
                90, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disagrees with the scheduled window");
    }

    @Test
    void refusesAZeroLengthBlock() {
        assertThatThrownBy(() -> new ItineraryItem(UUID.randomUUID(), 0, UUID.randomUUID(),
                ItineraryItemCategory.SIGHT, "Instant", LocalTime.of(9, 0), LocalTime.of(9, 0),
                0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endsAt must be after startsAt");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void refusesABlankTitleThatTheNotNullColumnWouldHappilyStore(String blank) {
        assertThatThrownBy(() -> new ItineraryItem(UUID.randomUUID(), 0, UUID.randomUUID(),
                ItineraryItemCategory.SIGHT, blank, LocalTime.of(9, 0), LocalTime.of(10, 0),
                60, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title must be");
    }

    // ------------------------------------------------------------------------------------ setup

    private static Itinerary itinerary(
            ItineraryStatus status, LocalDate start, LocalDate end, List<ItineraryDay> days) {
        return new Itinerary(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), status, start, end, "Asia/Tokyo", "v1", days, 0);
    }

    private static ItineraryDay day(int dayNumber, LocalDate date) {
        return new ItineraryDay(UUID.randomUUID(), dayNumber, date, null, LocalTime.of(9, 0),
                LocalTime.of(20, 0), List.of(item("Senso-ji", LocalTime.of(9, 0),
                        LocalTime.of(10, 30))));
    }

    private static ItineraryDay emptyDay(int dayNumber, LocalDate date) {
        return new ItineraryDay(UUID.randomUUID(), dayNumber, date, null, LocalTime.of(9, 0),
                LocalTime.of(20, 0), List.of());
    }

    private static ItineraryItem item(String title, LocalTime from, LocalTime to) {
        return new ItineraryItem(UUID.randomUUID(), 0, UUID.randomUUID(),
                ItineraryItemCategory.SIGHT, title, from, to,
                (int) java.time.Duration.between(from, to).toMinutes(), "wikivoyage:tokyo", null);
    }
}
