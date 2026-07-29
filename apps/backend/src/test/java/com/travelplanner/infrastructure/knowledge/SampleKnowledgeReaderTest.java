package com.travelplanner.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.TrustTier;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.PoiNode;
import com.travelplanner.infrastructure.knowledge.SampleSourceDocument.SourceNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The shipped seed files, and the guards that stop them becoming fabricated provenance.
 *
 * <p>These run in the ordinary {@code test} task: the files are on the classpath and the reader needs
 * neither Spring nor a database, so a seed file broken by an edit fails {@code ./gradlew build} on a
 * developer machine rather than at somebody's next startup.
 */
class SampleKnowledgeReaderTest {

    private static final Instant SEEDED_AT = Instant.parse("2026-07-29T00:00:00Z");

    private final SampleKnowledgeReader reader = new SampleKnowledgeReader();

    // ---------------------------------------------------------------------------------------
    // The reserved source (ADR 010 §3)
    // ---------------------------------------------------------------------------------------

    @Test
    void readsTheOneReservedSampleSource() {
        SourceNode source = reader.readSampleSource(SEEDED_AT);

        assertThat(source.sourceRef()).isEqualTo(KnowledgeProvenance.SAMPLE_SOURCE_REF);
        assertThat(source.licence()).isEqualTo(KnowledgeLicence.SAMPLE_DATA);
        assertThat(source.trustTier()).isEqualTo(TrustTier.SAMPLE);
        // The rule the whole task turns on: sample rows have nowhere real to point.
        assertThat(source.sourceUrl()).isNull();
        assertThat(source.attributionText()).isNotBlank();
    }

    @Test
    void datesTheSampleSourceFromTheSeedRunBecauseNothingWasEverFetched() {
        assertThat(reader.readSampleSource(SEEDED_AT).retrievedAt()).isEqualTo(SEEDED_AT);
    }

    @Test
    void rejectsASourceThatCitesAPlausibleUrl() {
        SourceNode fabricated = new SourceNode(
                KnowledgeProvenance.SAMPLE_SOURCE_REF,
                "Sample",
                KnowledgeLicence.SAMPLE_DATA,
                "attribution",
                "https://en.wikivoyage.org/wiki/Tokyo",
                SEEDED_AT,
                TrustTier.SAMPLE);

        assertThatThrownBy(() -> SampleKnowledgeReader.requireReservedSampleSource(fabricated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no source_url");
    }

    @Test
    void rejectsASourceRefThatIsNotTheReservedOne() {
        SourceNode fabricated = new SourceNode(
                "wikivoyage:tokyo",
                "Wikivoyage",
                KnowledgeLicence.SAMPLE_DATA,
                "attribution",
                null,
                SEEDED_AT,
                TrustTier.SAMPLE);

        assertThatThrownBy(() -> SampleKnowledgeReader.requireReservedSampleSource(fabricated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(KnowledgeProvenance.SAMPLE_SOURCE_REF);
    }

    @Test
    void rejectsASampleSourceClaimingACommunityTrustTier() {
        // KnowledgeProvenance and V13 both forbid the pairing; failing here names the file instead
        // of a constraint.
        SourceNode fabricated = new SourceNode(
                KnowledgeProvenance.SAMPLE_SOURCE_REF,
                "Sample",
                KnowledgeLicence.CC_BY_SA_4_0,
                "attribution",
                null,
                SEEDED_AT,
                TrustTier.COMMUNITY);

        assertThatThrownBy(() -> SampleKnowledgeReader.requireReservedSampleSource(fabricated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAMPLE_DATA/SAMPLE");
    }

    // ---------------------------------------------------------------------------------------
    // The three shipped destination files
    // ---------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"tokyo-jp", "bangkok-th", "shanghai-cn"})
    void everyShippedFileParsesAndCitesOnlyTheReservedSource(String slug) {
        SampleKnowledgeDocument document = reader.readDestination(slug);

        assertThat(document.destination().slug()).isEqualTo(slug);
        assertThat(document.guide().sourceRef()).isEqualTo(KnowledgeProvenance.SAMPLE_SOURCE_REF);
        assertThat(document.pois()).allSatisfy(poi ->
                assertThat(poi.sourceRef()).isEqualTo(KnowledgeProvenance.SAMPLE_SOURCE_REF));
        assertThat(document.travelApps()).allSatisfy(app ->
                assertThat(app.sourceRef()).isEqualTo(KnowledgeProvenance.SAMPLE_SOURCE_REF));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tokyo-jp", "bangkok-th", "shanghai-cn"})
    void everyShippedFileDeclaresPartialCoverage(String slug) {
        // ADR 010 §4: only FULL enters C2 ranking. Sample content must never be ranked.
        assertThat(reader.readDestination(slug).destination().coverageLevel())
                .isEqualTo(CoverageLevel.PARTIAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tokyo-jp", "bangkok-th", "shanghai-cn"})
    void everyShippedFileExercisesEveryTable(String slug) {
        SampleKnowledgeDocument document = reader.readDestination(slug);

        assertThat(document.areas()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(document.pois()).hasSizeGreaterThanOrEqualTo(8);
        assertThat(document.pois().stream().filter(poi -> poi.category() == PoiCategory.FOOD))
                .hasSizeGreaterThanOrEqualTo(3);
        assertThat(document.transportModes()).isNotEmpty();
        assertThat(document.routeSegments()).isNotEmpty();
        // ADR 010 §1: twelve months means the whole year, not twelve rows about June.
        assertThat(document.seasonality()).hasSize(12);
        assertThat(document.seasonality().stream().map(month -> month.month()).distinct())
                .hasSize(12);
        assertThat(document.priceHistory()).isNotEmpty();
        assertThat(document.travelApps()).hasSizeGreaterThanOrEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tokyo-jp", "bangkok-th", "shanghai-cn"})
    void everyPoiNamesAnAreaTheSameFileDeclares(String slug) {
        SampleKnowledgeDocument document = reader.readDestination(slug);
        List<String> areaSlugs = document.areas().stream().map(area -> area.slug()).toList();

        assertThat(document.pois())
                .filteredOn(poi -> poi.areaSlug() != null)
                .allSatisfy(poi -> assertThat(areaSlugs).contains(poi.areaSlug()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tokyo-jp", "bangkok-th", "shanghai-cn"})
    void noSampleAppLinksToAResolvableStore(String slug) {
        // RFC 2606 reserves `.invalid`, so these links cannot be mistaken for a real listing while
        // still satisfying V16's "at least one store link" CHECK.
        assertThat(reader.readDestination(slug).travelApps()).allSatisfy(app -> {
            assertThat(app.iosUrl()).contains("example.invalid");
            assertThat(app.androidUrl()).contains("example.invalid");
        });
    }

    // ---------------------------------------------------------------------------------------
    // The content guards
    // ---------------------------------------------------------------------------------------

    @Test
    void rejectsADestinationFilePromotedToFullCoverage() {
        SampleKnowledgeDocument promoted = withCoverage(
                reader.readDestination("tokyo-jp"), CoverageLevel.FULL);

        assertThatThrownBy(() ->
                SampleKnowledgeReader.requireSampleContent(promoted, "tokyo-jp.json", "tokyo-jp"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FULL");
    }

    @Test
    void rejectsAFileWhoseSlugDoesNotMatchItsName() {
        SampleKnowledgeDocument document = reader.readDestination("tokyo-jp");

        assertThatThrownBy(() ->
                SampleKnowledgeReader.requireSampleContent(document, "osaka-jp.json", "osaka-jp"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match its file name");
    }

    @Test
    void rejectsASingleRowThatCitesSomethingOtherThanTheReservedSource() {
        SampleKnowledgeDocument document = reader.readDestination("tokyo-jp");
        PoiNode original = document.pois().get(0);
        PoiNode fabricated = new PoiNode(
                original.slug(), original.name(), original.description(), original.category(),
                original.areaSlug(), original.tags(), original.locale(), original.latitude(),
                original.longitude(), original.openingHours(), original.priceBand(),
                "osm:node/12345");
        SampleKnowledgeDocument tampered = withFirstPoi(document, fabricated);

        assertThatThrownBy(() ->
                SampleKnowledgeReader.requireSampleContent(tampered, "tokyo-jp.json", "tokyo-jp"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("osm:node/12345");
    }

    private static SampleKnowledgeDocument withCoverage(
            SampleKnowledgeDocument document, CoverageLevel coverage) {

        SampleKnowledgeDocument.DestinationNode node = document.destination();
        return new SampleKnowledgeDocument(
                document.formatVersion(),
                new SampleKnowledgeDocument.DestinationNode(node.slug(), node.name(),
                        node.countryCode(), node.timezone(), node.latitude(), node.longitude(),
                        coverage),
                document.guide(), document.areas(), document.pois(), document.transportModes(),
                document.routeSegments(), document.seasonality(), document.priceHistory(),
                document.travelApps());
    }

    private static SampleKnowledgeDocument withFirstPoi(
            SampleKnowledgeDocument document, PoiNode replacement) {

        List<PoiNode> pois = document.pois().stream()
                .map(poi -> poi.slug().equals(replacement.slug()) ? replacement : poi)
                .toList();
        return new SampleKnowledgeDocument(
                document.formatVersion(), document.destination(), document.guide(),
                document.areas(), pois, document.transportModes(), document.routeSegments(),
                document.seasonality(), document.priceHistory(), document.travelApps());
    }
}
