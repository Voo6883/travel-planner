package com.travelplanner.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.AppReplacementReason;
import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.CrowdBand;
import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.TransportKind;
import com.travelplanner.domain.enums.TravelAppCategory;
import com.travelplanner.domain.enums.TrustTier;
import com.travelplanner.domain.enums.WeatherBand;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.AppReplacementNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.AreaNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.DestinationNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.GuideNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.PoiNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.PriceObservationNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.RouteSegmentNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.SeasonalityNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.TransportModeNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.TravelAppNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link SampleSeedDomainCheck} — gate <b>17B</b>'s seed validator, and the fix for <b>F-44</b>.
 *
 * <h2>What F-44 was</h2>
 *
 * <p>{@code price_history.amount} is one {@code numeric(12,2)} column for every currency, while
 * {@code Money} enforces the currency's own minor units. JPY has none, so {@code 4000.10 JPY} inserted
 * cleanly and then threw on <em>every</em> read of that destination's price history. It loaded green and
 * broke later, which is the worst shape a data defect can have — and Tokyo, priced in yen, is this
 * product's first destination.
 *
 * <h2>Two halves</h2>
 *
 * <p>{@link #everyCommittedSeedFileSatisfiesTheDomain} is the gate: the real reader over the real files,
 * driven off {@link SampleKnowledgeReader#DESTINATION_SLUGS} so a fourth destination is covered by adding
 * it to that list and nothing else. It needs no database, no Docker and no Spring context, so it runs on
 * every {@code ./gradlew test} and inside {@code npm run verify:fast}.
 *
 * <p>The rest construct deliberately-broken documents. A validator shown to pass on good input and never
 * shown to fail on bad input is a validator nobody should trust — and these are also where each check's
 * <em>reason</em> is written down, since a passing gate says nothing about why the check exists.
 */
class SampleSeedValidationTest {

    private final SampleKnowledgeReader reader = new SampleKnowledgeReader();

    /**
     * The gate.
     *
     * <p>Reads what is actually committed; a fixture would prove the validator works and say nothing
     * about whether the seed does. {@code SampleKnowledgeReaderTest} also reads all three files and so
     * would fail alongside this one — but it asserts about citations and coverage, and a failure there
     * reads as "the seed cites the wrong source". This test exists to make the domain-invariant failure
     * legible as itself.
     */
    @ParameterizedTest
    @MethodSource("committedDestinationSlugs")
    void everyCommittedSeedFileSatisfiesTheDomain(String slug) {
        assertThatCode(() -> reader.readDestination(slug))
                .describedAs("%s.json must satisfy every invariant its domain records carry, or it will "
                        + "load without complaint and fail when a row is read", slug)
                .doesNotThrowAnyException();
    }

    static List<String> committedDestinationSlugs() {
        return SampleKnowledgeReader.DESTINATION_SLUGS;
    }

    // ---------------------------------------------------------------------------------------
    // Proof that the validator bites — one per class of defect
    // ---------------------------------------------------------------------------------------

    /** F-44 itself. Yen has no minor units, so this is not a yen amount at all. */
    @Test
    void refusesAYenPriceWithFractionalDigits() {
        assertThatThrownBy(() -> validate(document()
                .withPrice(price("hotel_night", "4000.10", "JPY", LocalDate.of(2026, 4, 1)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("price hotel_night")
                .hasMessageContaining("4000.10 JPY")
                // The constraint detail, not `ValidationFailedException`'s user-facing message.
                .hasMessageContaining("more decimal places than JPY allows");
    }

    /**
     * The reason, not "The request is not valid."
     *
     * <p>{@code ValidationFailedException} carries a message meant for an HTTP client and keeps the
     * constraint detail in {@code details().fields}. Reported as-is, F-44's own failure read
     * {@code price HOTEL_NIGHT (4000.10 JPY): The request is not valid.} — the row named, the one useful
     * fact hidden. It is a plausible thing to simplify back to {@code getMessage()}, so it is pinned.
     */
    @Test
    void unwrapsAValidationFailureInsteadOfReportingItsGenericMessage() {
        assertThatThrownBy(() -> validate(document()
                .withPrice(price("hotel_night", "4000.10", "JPY", LocalDate.of(2026, 4, 1)))))
                .hasMessageNotContaining("The request is not valid");
    }

    /** The same amount in a two-decimal currency is fine — the rule is per currency, not global. */
    @Test
    void acceptsTheSameAmountInATwoDecimalCurrency() {
        assertThatCode(() -> validate(document()
                .withPrice(price("hotel_night", "4000.10", "USD", LocalDate.of(2026, 4, 1)))))
                .doesNotThrowAnyException();
    }

    /**
     * {@code ck_price_history_observed_on_first_of_month}.
     *
     * <p>A mid-month date breaks nothing loudly; it makes a monthly series quietly mean something else, so
     * two figures a curator believes are consecutive months may not be.
     */
    @Test
    void refusesAPriceNotDatedTheFirstOfTheMonth() {
        assertThatThrownBy(() -> validate(document()
                .withPrice(price("hotel_night", "100.00", "USD", LocalDate.of(2026, 4, 15)))))
                .hasMessageContaining("first of the month");
    }

    /** {@code Destination} resolves the zone against the JVM's tzdb; a plausible typo is not a zone. */
    @Test
    void refusesATimezoneThatIsNotAnIanaZone() {
        assertThatThrownBy(() -> validate(document().withTimezone("Asia/Tokio")))
                .hasMessageContaining("IANA zone");
    }

    /**
     * A swapped coordinate pair, which is the realistic version of this mistake.
     *
     * <p>Tokyo's longitude in the latitude slot is out of range and caught. Note what this cannot catch: a
     * swap where both values happen to be in range stays undetectable from the file alone.
     */
    @Test
    void refusesCoordinatesOutsideTheGlobe() {
        assertThatThrownBy(() -> validate(document()
                .withArea(area("shibuya", "139.70", "35.66"))))
                .hasMessageContaining("latitude out of range");
    }

    /**
     * A suppression key that is not a slug matches nothing, silently — the app pack renders and the
     * "Uber does not operate here" warning is simply absent. This turns that into a seed-time failure.
     */
    @Test
    void refusesASuppressionKeyThatIsNotASlug() {
        assertThatThrownBy(() -> validate(document().withTravelApp(travelApp("go-taxi",
                new AppReplacementNode("Uber ", "Uber", AppReplacementReason.NOT_AVAILABLE,
                        "Does not operate here.")))))
                .hasMessageContaining("lower-case slug");
    }

    /** {@code ck_travel_app_has_a_store_link}: an app nobody can install is not an app-pack entry. */
    @Test
    void refusesATravelAppWithNoStoreLink() {
        assertThatThrownBy(() -> validate(document().withTravelApp(new TravelAppNode("JP", "no-links",
                "No Links", TravelAppCategory.PAYMENT, null, null, null,
                KnowledgeProvenance.SAMPLE_SOURCE_REF, List.of()))))
                .hasMessageContaining("needs at least one store link");
    }

    /**
     * A cross-node reference, which no single record can check.
     *
     * <p>The writer resolves {@code areaSlug} to an area id, so an unknown slug becomes a POI attached to
     * nothing rather than an error — and C3 then cannot group it geographically.
     */
    @Test
    void refusesAPoiPointingAtAnAreaTheFileDoesNotDefine() {
        assertThatThrownBy(() -> validate(document().withPoi(poi("ramen", "nowhere"))))
                .hasMessageContaining("names area 'nowhere'");
    }

    /**
     * {@code uq_destination_area_destination_slug}.
     *
     * <p>Without this the seed aborts part-way, leaving the database half-populated and naming a
     * constraint rather than a file.
     */
    @Test
    void refusesTwoAreasWithTheSameSlug() {
        assertThatThrownBy(() -> validate(document()
                .withArea(area("shibuya", null, null))
                .withArea(area("shibuya", null, null))))
                .hasMessageContaining("appears twice");
    }

    @Test
    void refusesARouteSegmentThatStartsAndEndsInTheSameArea() {
        assertThatThrownBy(() -> validate(document()
                .withArea(area("shibuya", null, null))
                .withRouteSegment(new RouteSegmentNode("shibuya", "shibuya", "metro", 10, false, null,
                        KnowledgeProvenance.SAMPLE_SOURCE_REF))))
                .hasMessageContaining("same area");
    }

    @Test
    void refusesARouteSegmentNamingATransportModeTheFileDoesNotDefine() {
        assertThatThrownBy(() -> validate(document()
                .withArea(area("shibuya", null, null))
                .withArea(area("ginza", null, null))
                .withRouteSegment(new RouteSegmentNode("shibuya", "ginza", "monorail", 10, false, null,
                        KnowledgeProvenance.SAMPLE_SOURCE_REF))))
                .hasMessageContaining("transport mode 'monorail'");
    }

    /**
     * Every problem at once.
     *
     * <p>The property that makes this usable on a file a curator has just written: a validator that aborts
     * on the first bad row turns fixing a twenty-price seed into a twenty-round loop.
     */
    @Test
    void reportsEveryProblemInOnePassRatherThanTheFirst() {
        assertThatThrownBy(() -> validate(document()
                .withTimezone("Asia/Tokio")
                .withPrice(price("hotel_night", "4000.10", "JPY", LocalDate.of(2026, 4, 15)))
                .withArea(area("shibuya", "999", "0"))))
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("IANA zone")
                        .contains("JPY")
                        .contains("first of the month")
                        .contains("latitude out of range"));
    }

    /** The failure has to name the file, since a seed run reads three of them. */
    @Test
    void namesTheFileAndSaysWhenTheProblemWouldOtherwiseHaveSurfaced() {
        assertThatThrownBy(() -> validate(document().withTimezone("Asia/Tokio")))
                .hasMessageContaining("test.json")
                .hasMessageContaining("READ");
    }

    // ---------------------------------------------------------------------------------------

    private static void validate(Builder builder) {
        SampleSeedDomainCheck.requireDomainValid(builder.build(), "test.json", sampleProvenance());
    }

    private static KnowledgeProvenance sampleProvenance() {
        return new KnowledgeProvenance(KnowledgeProvenance.SAMPLE_SOURCE_REF, "Sample",
                KnowledgeLicence.SAMPLE_DATA, "Sample data", null, TrustTier.SAMPLE, Instant.EPOCH);
    }

    private static Builder document() {
        return new Builder();
    }

    private static AreaNode area(String slug, String latitude, String longitude) {
        return new AreaNode(slug, "Area " + slug, null, decimal(latitude), decimal(longitude),
                KnowledgeProvenance.SAMPLE_SOURCE_REF);
    }

    private static PoiNode poi(String slug, String areaSlug) {
        return new PoiNode(slug, "Poi " + slug, "Description.", PoiCategory.FOOD, areaSlug, List.of(),
                "en", null, null, null, PriceBand.BUDGET, KnowledgeProvenance.SAMPLE_SOURCE_REF);
    }

    private static PriceObservationNode price(String category, String amount, String currency,
            LocalDate observedOn) {
        return new PriceObservationNode(category, new BigDecimal(amount), currency, observedOn,
                KnowledgeProvenance.SAMPLE_SOURCE_REF);
    }

    private static TravelAppNode travelApp(String slug, AppReplacementNode... replaces) {
        return new TravelAppNode("JP", slug, "App " + slug, TravelAppCategory.RIDEHAILING, null,
                "https://example.invalid/ios", null, KnowledgeProvenance.SAMPLE_SOURCE_REF,
                List.of(replaces));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    /**
     * A minimal valid document, with one thing changed per test.
     *
     * <p>Starting from valid and breaking exactly one field is what makes each assertion about the field it
     * names. Assembling a bad document from scratch would let an unrelated invariant fire first and the
     * test would pass for the wrong reason — the failure mode that makes negative tests untrustworthy.
     */
    private static final class Builder {

        private String timezone = "Asia/Tokyo";
        private final List<AreaNode> areas = new ArrayList<>();
        private final List<PoiNode> pois = new ArrayList<>();
        private final List<RouteSegmentNode> segments = new ArrayList<>();
        private final List<PriceObservationNode> prices = new ArrayList<>();
        private final List<TravelAppNode> travelApps = new ArrayList<>();

        private Builder withTimezone(String value) {
            this.timezone = value;
            return this;
        }

        private Builder withArea(AreaNode area) {
            areas.add(area);
            return this;
        }

        private Builder withPoi(PoiNode poi) {
            pois.add(poi);
            return this;
        }

        private Builder withRouteSegment(RouteSegmentNode segment) {
            segments.add(segment);
            return this;
        }

        private Builder withPrice(PriceObservationNode price) {
            prices.add(price);
            return this;
        }

        private Builder withTravelApp(TravelAppNode app) {
            travelApps.add(app);
            return this;
        }

        private SampleKnowledgeDocument build() {
            return new SampleKnowledgeDocument(
                    1,
                    new DestinationNode("tokyo-jp", "Tokyo", "JP", timezone, null, null,
                            CoverageLevel.PARTIAL),
                    new GuideNode("en", "Overview.", "Food.", "Practical.",
                            KnowledgeProvenance.SAMPLE_SOURCE_REF),
                    areas,
                    pois,
                    // One mode by default, so a route-segment test can name a defined mode and produce
                    // exactly the one problem it is about.
                    List.of(new TransportModeNode("metro", "Metro", TransportKind.METRO, null,
                            PriceBand.BUDGET, true, KnowledgeProvenance.SAMPLE_SOURCE_REF)),
                    segments,
                    // Twelve months, so the all-twelve check fires only where a test wants it to.
                    IntStream.rangeClosed(1, 12)
                            .mapToObj(month -> new SeasonalityNode(month, WeatherBand.MILD,
                                    CrowdBand.MODERATE, PriceBand.MODERATE, null,
                                    KnowledgeProvenance.SAMPLE_SOURCE_REF))
                            .toList(),
                    prices,
                    travelApps);
        }
    }
}
