package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.CrowdBand;
import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.KnowledgeMatchType;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.TransportKind;
import com.travelplanner.domain.enums.TrustTier;
import com.travelplanner.domain.enums.WeatherBand;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.DestinationGuide;
import com.travelplanner.domain.model.KnowledgeMatch;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.PriceObservation;
import com.travelplanner.domain.model.RouteSegment;
import com.travelplanner.domain.model.SeasonalityMonth;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.domain.model.TravelAppReplacement;
import com.travelplanner.domain.port.KnowledgePort;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.domain.valueobject.KnowledgeQuery;
import com.travelplanner.domain.valueobject.Money;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The ten knowledge mappers, end to end against a real PostgreSQL — the last open half of <b>F-32</b>,
 * and gate <b>17B</b>'s "shared adapter contract tests".
 *
 * <p>Task 16 shipped ten entities, ten repositories and ten mappers with the Testcontainers suite
 * waived, and {@code PersistenceMappingIntegrationTest} covers user, trip and brief — nothing under
 * {@code KnowledgePort}. So until now the entire knowledge read path had no test that ran SQL:
 * {@code ddl-auto: validate} proved the columns line up, which is real evidence and says nothing about
 * whether a mapper reads the right one.
 *
 * <h2>Rows are written as SQL, read back through the port</h2>
 *
 * <p>Deliberately asymmetric. The mappers are read-direction only, so writing through them is not
 * possible and writing through the entities would exercise a path production never takes. Inserting
 * with {@code INSERT} also lets a test put <em>distinguishable</em> values in columns that a mapper
 * could plausibly confuse — which is the whole technique here, and the reason the two timestamps in
 * {@link #provenanceTakesItsFreshnessFromTheRowAndItsLicenceFromTheSource} are years apart.
 *
 * <p>Everything is one destination, seeded fresh per test. {@code TRUNCATE … CASCADE} rather than
 * {@code DELETE} so a previous test's rows cannot survive a foreign key nobody thought about.
 */
class KnowledgePersistenceIT extends AbstractPostgresIntegrationTest {

    /** When the SOURCE was last fetched. Must never reach a fact's freshness. */
    private static final Instant SOURCE_FETCHED_AT = Instant.parse("2019-01-01T00:00:00Z");

    /** When THIS FACT was taken from it. ADR 010 §6 measures every TTL against this. */
    private static final Instant ROW_FETCHED_AT = Instant.parse("2026-06-15T10:30:00Z");

    private static final String SLUG = "tokyo-jp";

    @Autowired
    private KnowledgePort knowledge;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID sourceId;
    private UUID destinationId;
    private UUID areaId;

    @BeforeEach
    void seedOneFullyPopulatedDestination() {
        jdbc.execute("TRUNCATE knowledge_source, destination, travel_app CASCADE");

        sourceId = UUID.randomUUID();
        destinationId = UUID.randomUUID();
        areaId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO knowledge_source (id, source_ref, name, licence, attribution_text,
                        source_url, retrieved_at, trust_tier)
                VALUES (?, 'wikivoyage:tokyo', 'Wikivoyage', 'CC_BY_SA_4_0',
                        '© Wikivoyage contributors', 'https://en.wikivoyage.org/wiki/Tokyo', ?, 'COMMUNITY')
                """, sourceId, java.sql.Timestamp.from(SOURCE_FETCHED_AT));

        // Coordinates chosen to exercise numeric(9,6) at full precision in both signs.
        jdbc.update("""
                INSERT INTO destination (id, slug, name, country_code, timezone, latitude, longitude,
                        coverage_level)
                VALUES (?, ?, 'Tokyo', 'JP', 'Asia/Tokyo', 35.689487, 139.691711, 'FULL')
                """, destinationId, SLUG);
    }

    // ---------------------------------------------------------------------------------------
    // Provenance — the one assembly that reads from two rows
    // ---------------------------------------------------------------------------------------

    /**
     * {@code KnowledgeProvenanceMapper}'s own javadoc names this as the line that must not change, and
     * explains that MapStruct "would still compile, having quietly picked one of the two". This is the
     * test that makes the difference observable.
     *
     * <p>Both {@code knowledge_source} and every catalogue table have a {@code retrieved_at}. The
     * source's records when the source was fetched; the row's records when <em>this fact</em> was taken
     * from it, and ADR 010 §6 measures every TTL against the row. Read the source's instead and a
     * re-fetched source silently rejuvenates every fact ever taken from it — staleness becomes wrong for
     * the whole catalogue with nothing failing to say so.
     *
     * <p>Seven years apart, so a swap cannot pass by coincidence.
     */
    @Test
    void provenanceTakesItsFreshnessFromTheRowAndItsLicenceFromTheSource() {
        insertArea(areaId, "shibuya", "Shibuya", "Crossings.", 35.658034, 139.701636);

        KnowledgeProvenance provenance = knowledge.findAreas(destinationId).get(0).provenance();

        assertThat(provenance.retrievedAt())
                .describedAs("the ROW's fetch time; if this is %s the mapper read source.retrievedAt "
                        + "and every TTL in ADR 010 §6 is now measured against the wrong instant",
                        SOURCE_FETCHED_AT)
                .isEqualTo(ROW_FETCHED_AT);

        // Everything else does come from the source — the other half of the same rule.
        assertThat(provenance.sourceRef()).isEqualTo("wikivoyage:tokyo");
        assertThat(provenance.name()).isEqualTo("Wikivoyage");
        assertThat(provenance.licence()).isEqualTo(KnowledgeLicence.CC_BY_SA_4_0);
        assertThat(provenance.attributionText()).isEqualTo("© Wikivoyage contributors");
        assertThat(provenance.sourceUrl()).isEqualTo("https://en.wikivoyage.org/wiki/Tokyo");
        assertThat(provenance.trustTier()).isEqualTo(TrustTier.COMMUNITY);
    }

    /**
     * A source with no URL yields absent, not empty-string.
     *
     * <p>ADR 010 §3 requires sample data to cite {@code stub:sample} and have nowhere real to point, so
     * this is the normal case for the seeded dataset rather than an edge one. A mapper that turned the
     * null into {@code ""} would make "we have a citation" true for a fact that cannot be checked.
     */
    @Test
    void anAbsentSourceUrlStaysAbsent() {
        jdbc.update("UPDATE knowledge_source SET source_url = NULL WHERE id = ?", sourceId);
        insertArea(areaId, "shibuya", "Shibuya", null, null, null);

        assertThat(knowledge.findAreas(destinationId).get(0).provenance().sourceUrl()).isNull();
    }

    // ---------------------------------------------------------------------------------------
    // Round trips, one per mapper
    // ---------------------------------------------------------------------------------------

    @Test
    void aDestinationRoundTripsItsCoordinatesAtFullNumericPrecision() {
        Destination destination = knowledge.findDestinationBySlug(SLUG).orElseThrow();

        assertThat(destination.id()).isEqualTo(destinationId);
        assertThat(destination.name()).isEqualTo("Tokyo");
        assertThat(destination.countryCode()).isEqualTo("JP");
        assertThat(destination.timezone()).isEqualTo("Asia/Tokyo");
        // numeric(9,6) -> Double. Six decimal places is ~10 cm; losing any of them would move a POI
        // across a street, which is exactly the kind of wrong that looks like data.
        assertThat(destination.latitudeIfKnown()).contains(35.689487);
        assertThat(destination.longitudeIfKnown()).contains(139.691711);
        assertThat(destination.coverageLevel()).isEqualTo(CoverageLevel.FULL);
        assertThat(knowledge.findDestinationById(destinationId)).contains(destination);
    }

    /**
     * ADR 010 §4: only {@code FULL} destinations are offered. The rule lives in the domain
     * ({@code Destination.isRankingEligible}) and in this query, and both have to agree.
     */
    @Test
    void onlyFullyCuratedDestinationsAreOffered() {
        UUID partial = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO destination (id, slug, name, country_code, timezone, coverage_level)
                VALUES (?, 'osaka-jp', 'Osaka', 'JP', 'Asia/Tokyo', 'PARTIAL')
                """, partial);

        assertThat(knowledge.findSupportedDestinations())
                .extracting(Destination::slug)
                .describedAs("a PARTIAL destination in this list is a half-curated city presented as "
                        + "ready, which is the failure ADR 010 §4 exists to prevent")
                .containsExactly(SLUG);
    }

    @Test
    void aGuideRoundTripsItsThreeSectionsAndItsVersion() {
        jdbc.update("""
                INSERT INTO destination_guide (id, destination_id, locale, overview, food, practical,
                        source_id, retrieved_at, version)
                VALUES (?, ?, 'en', 'Overview text.', 'Food text.', 'Practical text.', ?, ?, 3)
                """, UUID.randomUUID(), destinationId, sourceId, java.sql.Timestamp.from(ROW_FETCHED_AT));

        DestinationGuide guide = knowledge.findGuide(destinationId, "en").orElseThrow();

        assertThat(guide.overview()).isEqualTo("Overview text.");
        assertThat(guide.food()).isEqualTo("Food text.");
        assertThat(guide.practical()).isEqualTo("Practical text.");
        // Task 41's curation UI locks on this. An ordinal-or-zero here would make every optimistic
        // check pass, which is worse than no locking because it looks like locking.
        assertThat(guide.version()).isEqualTo(3);
        assertThat(knowledge.findGuide(destinationId, "ms")).isEmpty();
    }

    /**
     * {@code tags text[]} is the only collection column in the catalogue.
     *
     * <p>A JDBC array is the classic silent mapping failure: it arrives as {@code java.sql.Array} or
     * {@code String[]} depending on the driver and dialect, and a mapper that mishandles it typically
     * produces an empty list rather than an error — so retrieval keeps working and every tag filter
     * quietly matches nothing.
     */
    @Test
    void aPoiRoundTripsItsTagArrayAndItsOptionalFields() {
        UUID poiId = UUID.randomUUID();
        insertArea(areaId, "shibuya", "Shibuya", "Crossings.", 35.658034, 139.701636);
        jdbc.update("""
                INSERT INTO poi (id, destination_id, area_id, slug, name, description, category, tags,
                        locale, latitude, longitude, opening_hours, price_band, source_id, retrieved_at)
                VALUES (?, ?, ?, 'ramen-shop', 'Ramen Shop', 'Small counter.', 'FOOD',
                        ARRAY['ramen','late-night','cash-only'], 'en', 35.659000, 139.700000,
                        '11:00-23:00', 'BUDGET', ?, ?)
                """, poiId, destinationId, areaId, sourceId, java.sql.Timestamp.from(ROW_FETCHED_AT));

        Poi poi = knowledge.findPois(destinationId, Optional.empty()).get(0);

        assertThat(poi.tags()).containsExactly("ramen", "late-night", "cash-only");
        assertThat(poi.category()).isEqualTo(PoiCategory.FOOD);
        assertThat(poi.priceBand()).isEqualTo(PriceBand.BUDGET);
        assertThat(poi.areaId()).isEqualTo(areaId);
        assertThat(poi.openingHours()).isEqualTo("11:00-23:00");
        assertThat(poi.latitudeIfKnown()).contains(35.659000);
    }

    @Test
    void aPoiWithNoOptionalDataStaysEmptyRatherThanBecomingDefaults() {
        jdbc.update("""
                INSERT INTO poi (id, destination_id, slug, name, category, source_id, retrieved_at)
                VALUES (?, ?, 'unnamed-sight', 'Sight', 'SIGHT', ?, ?)
                """, UUID.randomUUID(), destinationId, sourceId, java.sql.Timestamp.from(ROW_FETCHED_AT));

        Poi poi = knowledge.findPois(destinationId, Optional.empty()).get(0);

        assertThat(poi.tags()).isEmpty();
        assertThat(poi.areaId()).isNull();
        assertThat(poi.priceBand()).isNull();
        // A price band invented here would put "FREE" on a POI nobody priced, which a traveller acts on.
        assertThat(poi.latitudeIfKnown()).isEmpty();
        assertThat(poi.longitudeIfKnown()).isEmpty();
    }

    @Test
    void thePoiCategoryFilterIsAppliedInSqlRatherThanIgnored() {
        insertPoi("ramen-shop", "FOOD");
        insertPoi("temple", "SIGHT");
        insertPoi("gallery", "MUSEUM");

        assertThat(knowledge.findPois(destinationId, Optional.of(PoiCategory.FOOD)))
                .extracting(Poi::slug)
                .containsExactly("ramen-shop");
        assertThat(knowledge.findPois(destinationId, Optional.empty())).hasSize(3);
    }

    @Test
    void aTransportModeRoundTripsItsKindAndItsBooleanFlag() {
        jdbc.update("""
                INSERT INTO transport_mode (id, destination_id, slug, name, kind, description,
                        cost_band, tourist_friendly, source_id, retrieved_at)
                VALUES (?, ?, 'metro', 'Tokyo Metro', 'METRO', 'Dense and punctual.', 'BUDGET', true, ?, ?)
                """, UUID.randomUUID(), destinationId, sourceId, java.sql.Timestamp.from(ROW_FETCHED_AT));

        TransportMode mode = knowledge.findTransportModes(destinationId).get(0);

        assertThat(mode.kind()).isEqualTo(TransportKind.METRO);
        assertThat(mode.costBand()).isEqualTo(PriceBand.BUDGET);
        assertThat(mode.touristFriendly()).isTrue();
    }

    /**
     * {@code duration_minutes integer} becomes a {@link Duration}.
     *
     * <p>The interesting direction is the one this asserts: whole minutes in, exactly that many minutes
     * out. A mapper reading the number as seconds would produce a 25-second walk between neighbourhoods,
     * and C3 would schedule a day around it.
     */
    @Test
    void aRouteSegmentRoundTripsItsDurationAsMinutesNotSeconds() {
        UUID toAreaId = UUID.randomUUID();
        UUID modeId = UUID.randomUUID();
        insertArea(areaId, "shibuya", "Shibuya", null, null, null);
        insertArea(toAreaId, "shinjuku", "Shinjuku", null, null, null);
        jdbc.update("""
                INSERT INTO transport_mode (id, destination_id, slug, name, kind, tourist_friendly,
                        source_id, retrieved_at)
                VALUES (?, ?, 'metro', 'Tokyo Metro', 'METRO', true, ?, ?)
                """, modeId, destinationId, sourceId, java.sql.Timestamp.from(ROW_FETCHED_AT));
        jdbc.update("""
                INSERT INTO route_segment (id, destination_id, from_area_id, to_area_id,
                        transport_mode_id, duration_minutes, estimated, notes, source_id, retrieved_at)
                VALUES (?, ?, ?, ?, ?, 25, false, 'Direct on the Fukutoshin line.', ?, ?)
                """, UUID.randomUUID(), destinationId, areaId, toAreaId, modeId, sourceId,
                java.sql.Timestamp.from(ROW_FETCHED_AT));

        RouteSegment segment = knowledge.findRouteSegments(destinationId).get(0);

        assertThat(segment.duration()).isEqualTo(Duration.ofMinutes(25));
        // ADR 010's Consequences: only curated pairs are stored, and `estimated` is what keeps a
        // curated leg distinguishable from one the agent derived.
        assertThat(segment.estimated()).isFalse();
        assertThat(segment.fromAreaId()).isEqualTo(areaId);
        assertThat(segment.toAreaId()).isEqualTo(toAreaId);
    }

    /** Twelve rows, January first, whatever order they were written in. */
    @Test
    void seasonalityComesBackJanuaryToDecemberRegardlessOfInsertOrder() {
        for (int month : new int[] {7, 1, 12, 3, 9, 2, 11, 4, 8, 5, 10, 6}) {
            jdbc.update("""
                    INSERT INTO seasonality (id, destination_id, month, weather_band, crowd_band,
                            price_band, source_id, retrieved_at)
                    VALUES (?, ?, ?, 'MILD', 'MODERATE', 'MODERATE', ?, ?)
                    """, UUID.randomUUID(), destinationId, month, sourceId,
                    java.sql.Timestamp.from(ROW_FETCHED_AT));
        }

        assertThat(knowledge.findSeasonality(destinationId))
                .extracting(SeasonalityMonth::month)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        assertThat(knowledge.findSeasonality(destinationId).get(0).weatherBand())
                .isEqualTo(WeatherBand.MILD);
        assertThat(knowledge.findSeasonality(destinationId).get(0).crowdBand())
                .isEqualTo(CrowdBand.MODERATE);
    }

    /**
     * {@code numeric(12,2)} plus {@code char(3)} becomes {@link Money}, exactly.
     *
     * <p>PLAN §4.0.2-A and §13.1: money is never floating point. The round trip is where a
     * {@code double} would show itself — 4000.10 is not representable, and a mapper that went through
     * one would return 4000.099999999999.
     */
    @Test
    void aPriceObservationRoundTripsItsMoneyExactly() {
        insertPrice("hotel_night", "4000.10", "USD");

        PriceObservation observation = knowledge.findPriceHistory(destinationId, "hotel_night").get(0);

        assertThat(observation.amount()).isEqualTo(Money.of("4000.10", "USD"));
        assertThat(observation.observedOn()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(knowledge.findPriceHistory(destinationId, "flight")).isEmpty();
    }

    /**
     * A zero-decimal currency loses the column's trailing zeros rather than keeping a fake precision.
     *
     * <p>{@code price_history.amount} is {@code numeric(12,2)} for every currency, while {@link Money}
     * enforces the currency's own minor units — and JPY has none. So a yen price stored as
     * {@code 4000.00} must come back as {@code 4000}, not as an amount claiming two decimal places the
     * currency does not have. Tokyo is this product's first destination, so this is the common case, not
     * an exotic one.
     */
    @Test
    void aZeroDecimalCurrencyComesBackWithoutTheColumnsTrailingZeros() {
        insertPrice("hotel_night", "4000.00", "JPY");

        assertThat(knowledge.findPriceHistory(destinationId, "hotel_night").get(0).amount())
                .isEqualTo(Money.of("4000", "JPY"));
    }

    /**
     * A yen price with real fractional digits is refused at read time, loudly.
     *
     * <p>The schema cannot express per-currency scale — one {@code numeric(12,2)} column serves every
     * currency — so nothing stops a curator writing {@code 4000.10 JPY}. {@link Money} catches it, and
     * the failure names the currency rather than silently truncating to 4000 or rounding to 4000.
     * Truncating would be worse: a price that quietly disagrees with its source is exactly the kind of
     * fact PLAN §4.1.0 forbids inventing.
     *
     * <p><b>But it fails on read, not on write</b>, which means a bad seed loads cleanly and then breaks
     * every read of that destination's price history. Gate 17B's seed validator is where that belongs;
     * recorded as <b>F-44</b>.
     */
    @Test
    void aYenPriceWithFractionalDigitsIsRefusedRatherThanTruncated() {
        insertPrice("hotel_night", "4000.10", "JPY");

        assertThat(catchThrowableOf(() -> knowledge.findPriceHistory(destinationId, "hotel_night")))
                .isInstanceOf(com.travelplanner.domain.exception.ValidationFailedException.class)
                .hasMessageContaining("not valid");
    }

    @Test
    void travelAppsComeBackOrderedByCategoryNameThenSlug() {
        insertTravelApp("suica", "Suica", "PAYMENT");
        insertTravelApp("go-taxi", "GO", "RIDEHAILING");
        insertTravelApp("navitime", "NAVITIME", "TRANSIT");

        assertThat(knowledge.findTravelApps("JP"))
                .extracting(TravelApp::slug)
                // `order by category asc, slug asc`, and category sorts by its STORED NAME — not by the
                // enum's declaration order, which is RIDEHAILING, TRANSIT, PAYMENT, … So the pack reads
                // PAYMENT, RIDEHAILING, TRANSIT. Worth pinning because the two orders differ and the
                // declaration order is the one a reader of TravelAppCategory would assume.
                .containsExactly("suica", "go-taxi", "navitime");
        assertThat(knowledge.findTravelApps("TH")).isEmpty();
    }

    /**
     * V21's suppression row, read back through the country join.
     *
     * <p>{@code travel_app_replacement} has no {@code country_code} of its own — it is reached through
     * {@code local_app_id} — so this is the only test that proves the join is right. It also re-validates
     * the slug shape on the way out, which is the invariant that makes a suppression match anything.
     */
    @Test
    void aTravelAppReplacementIsReachableByCountryThroughItsLocalApp() {
        UUID localAppId = insertTravelApp("go-taxi", "GO", "RIDEHAILING");
        jdbc.update("""
                INSERT INTO travel_app_replacement (id, local_app_id, replaced_app_key,
                        replaced_app_name, reason, detail, source_id, retrieved_at)
                VALUES (?, ?, 'uber', 'Uber', 'NOT_THE_LOCAL_STANDARD',
                        'Coverage is thin outside the largest cities.', ?, ?)
                """, UUID.randomUUID(), localAppId, sourceId, java.sql.Timestamp.from(ROW_FETCHED_AT));

        List<TravelAppReplacement> replacements = knowledge.findTravelAppReplacements("JP");

        assertThat(replacements).hasSize(1);
        TravelAppReplacement replacement = replacements.get(0);
        assertThat(replacement.localAppId()).isEqualTo(localAppId);
        assertThat(replacement.replacedAppKey()).isEqualTo("uber");
        assertThat(replacement.replacedAppName()).isEqualTo("Uber");
        assertThat(replacement.suppresses("Uber")).isTrue();
        // NOT_THE_LOCAL_STANDARD is advice, not a warning — the distinction the UI renders differently.
        assertThat(replacement.replacedAppIsUnusable()).isFalse();
        assertThat(knowledge.findTravelAppReplacements("TH")).isEmpty();
    }

    /** One statement per (local app, replaced app) pair, enforced by the database. */
    @Test
    void theSameSuppressionCannotBeRecordedTwice() {
        UUID localAppId = insertTravelApp("go-taxi", "GO", "RIDEHAILING");
        insertReplacement(localAppId, "uber");

        assertThat(catchThrowableOf(() -> insertReplacement(localAppId, "uber")))
                .describedAs("two rows for one pair render as two contradictory warnings side by side")
                .isNotNull();
        assertThat(knowledge.findTravelAppReplacements("JP")).hasSize(1);
    }

    // ---------------------------------------------------------------------------------------
    // Hybrid search — the native query nothing else exercises
    // ---------------------------------------------------------------------------------------

    /**
     * {@code search} runs hand-written SQL that neither JPQL nor the Criteria API can express, and it is
     * the only read path in the adapter with no mapper behind it — the projection is assembled by hand.
     * Nothing tested it.
     *
     * <p>Two properties matter and both are asserted: results come back most-similar first, and the
     * similarity floor removes rather than reorders. The floor is what stops an empty corpus from
     * returning twenty confident-looking rows.
     */
    @Test
    void searchReturnsTheMostSimilarFirstAndDropsAnythingBelowTheFloor() {
        UUID near = insertEmbeddedPoi("near-match", 0.9f);
        UUID far = insertEmbeddedPoi("far-match", 0.1f);

        List<KnowledgeMatch> generous = knowledge.search(new KnowledgeQuery(destinationId, "ramen",
                uniformVector(0.9f), 20, 0.0, Set.of(KnowledgeMatchType.POI)));

        assertThat(generous).extracting(KnowledgeMatch::id).containsExactly(near, far);
        assertThat(generous.get(0).score()).isGreaterThan(generous.get(1).score());
        assertThat(generous.get(0).sourceType()).isEqualTo(KnowledgeMatchType.POI);
        assertThat(generous.get(0).destinationId()).isEqualTo(destinationId);
        assertThat(generous.get(0).provenance().retrievedAt()).isEqualTo(ROW_FETCHED_AT);

        double floor = (generous.get(0).score() + generous.get(1).score()) / 2;
        assertThat(knowledge.search(new KnowledgeQuery(destinationId, "ramen", uniformVector(0.9f), 20,
                floor, Set.of(KnowledgeMatchType.POI))))
                .extracting(KnowledgeMatch::id)
                .containsExactly(near);
    }

    /**
     * A destination with no embeddings returns nothing rather than another destination's rows.
     *
     * <p>ADR 010 §5 requires the destination filter <em>before</em> the ANN search, and the whole reason
     * {@code KnowledgeQuery.destinationId} is non-null is that the unscoped query cannot be written by
     * accident. This is what proves the filter is actually in the SQL.
     */
    @Test
    void searchIsScopedToOneDestination() {
        insertEmbeddedPoi("tokyo-poi", 0.9f);
        UUID other = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO destination (id, slug, name, country_code, timezone, coverage_level)
                VALUES (?, 'osaka-jp', 'Osaka', 'JP', 'Asia/Tokyo', 'FULL')
                """, other);

        assertThat(knowledge.search(new KnowledgeQuery(other, "ramen", uniformVector(0.9f), 20, 0.0,
                Set.of(KnowledgeMatchType.POI)))).isEmpty();
    }

    // ---------------------------------------------------------------------------------------

    private void insertArea(UUID id, String slug, String name, String description, Double latitude,
            Double longitude) {
        jdbc.update("""
                INSERT INTO destination_area (id, destination_id, slug, name, description, latitude,
                        longitude, source_id, retrieved_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, destinationId, slug, name, description, latitude, longitude, sourceId,
                java.sql.Timestamp.from(ROW_FETCHED_AT));
    }

    private void insertPrice(String category, String amount, String currency) {
        jdbc.update("""
                INSERT INTO price_history (id, destination_id, category, amount, currency, observed_on,
                        source_id, retrieved_at)
                VALUES (?, ?, ?, CAST(? AS numeric), ?, DATE '2026-04-01', ?, ?)
                """, UUID.randomUUID(), destinationId, category, amount, currency, sourceId,
                java.sql.Timestamp.from(ROW_FETCHED_AT));
    }

    private void insertPoi(String slug, String category) {
        jdbc.update("""
                INSERT INTO poi (id, destination_id, slug, name, category, source_id, retrieved_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), destinationId, slug, slug, category, sourceId,
                java.sql.Timestamp.from(ROW_FETCHED_AT));
    }

    private UUID insertTravelApp(String slug, String name, String category) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO travel_app (id, country_code, slug, name, category, ios_url, source_id,
                        retrieved_at)
                VALUES (?, 'JP', ?, ?, ?, 'https://example.invalid/ios', ?, ?)
                """, id, slug, name, category, sourceId, java.sql.Timestamp.from(ROW_FETCHED_AT));
        return id;
    }

    private void insertReplacement(UUID localAppId, String replacedKey) {
        jdbc.update("""
                INSERT INTO travel_app_replacement (id, local_app_id, replaced_app_key,
                        replaced_app_name, reason, detail, source_id, retrieved_at)
                VALUES (?, ?, ?, 'Uber', 'NOT_AVAILABLE', 'Does not operate here.', ?, ?)
                """, UUID.randomUUID(), localAppId, replacedKey, sourceId,
                java.sql.Timestamp.from(ROW_FETCHED_AT));
    }

    /**
     * A POI plus its embedding, where every component of the vector is {@code value}.
     *
     * <p>Uniform vectors make cosine similarity trivially predictable: a query of all-0.9 is identical in
     * direction to a stored all-0.9 and less similar to an all-0.1 only because of the shared constant —
     * so ordering is deterministic without the test having to reason about geometry.
     */
    private UUID insertEmbeddedPoi(String slug, float value) {
        UUID poiId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO poi (id, destination_id, slug, name, description, category, source_id,
                        retrieved_at)
                VALUES (?, ?, ?, ?, 'A place worth eating at.', 'FOOD', ?, ?)
                """, poiId, destinationId, slug, slug, sourceId,
                java.sql.Timestamp.from(ROW_FETCHED_AT));
        jdbc.update("""
                INSERT INTO poi_embedding (id, poi_id, destination_id, destination_slug, embedding,
                        embedding_model, embedding_dimension, content_hash)
                VALUES (?, ?, ?, ?, CAST(? AS vector), 'text-embedding-3-small', 1536, ?)
                """, UUID.randomUUID(), poiId, destinationId, SLUG, vectorLiteral(value),
                "0".repeat(63) + (value > 0.5f ? "1" : "2"));
        return poiId;
    }

    /**
     * A slightly perturbed uniform vector.
     *
     * <p>Perturbed because a perfectly uniform query vector is equidistant from two uniform stored
     * vectors under cosine similarity — both have the same direction — so the ordering this test asserts
     * would be arbitrary. Varying one component breaks the tie in a direction the test controls.
     */
    private static float[] uniformVector(float value) {
        float[] vector = new float[KnowledgeQuery.EMBEDDING_DIMENSION];
        java.util.Arrays.fill(vector, value);
        vector[0] = value + 0.05f;
        return vector;
    }

    private static String vectorLiteral(float value) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < KnowledgeQuery.EMBEDDING_DIMENSION; index += 1) {
            literal.append(index == 0 ? "" : ",").append(index == 0 ? value + 0.02f : value);
        }
        return literal.append("]").toString();
    }

    /** {@code assertThatThrownBy} needs a throwing lambda; this keeps the call sites readable. */
    private static Throwable catchThrowableOf(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException failure) {
            return failure;
        }
    }
}
