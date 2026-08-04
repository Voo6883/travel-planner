package com.travelplanner.api.dto.destination;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.application.knowledge.DestinationGuideService.DestinationGuideDetail;
import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.TransportKind;
import com.travelplanner.domain.enums.TravelAppCategory;
import com.travelplanner.domain.enums.TrustTier;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.domain.model.DestinationGuide;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Citation assembly for {@code GET /destinations/{id}/guide} (ADR 010 §2, §3, §6).
 *
 * <p>These are the assertions the response shipped without. It used to emit exactly one ref — the
 * guide's own provenance with {@code field_group} hardcoded to {@code "overview"} — so the food and
 * practical prose was attributed to a source that had not been checked for it, and the POIs, areas,
 * transport modes and app pack were displayed with no citation at all. Everything below would have
 * passed silently against that version except by being absent.
 */
class DestinationGuideDetailResponseTest {

    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private static final UUID DESTINATION_ID = UUID.randomUUID();

    private static final Destination TOKYO = new Destination(DESTINATION_ID, "tokyo-jp", "Tokyo",
            "JP", "Asia/Tokyo", 35.6762, 139.6503, CoverageLevel.FULL);

    @Test
    void citesEveryFieldGroupThatActuallyBacksSomethingOnThePage() {
        DestinationGuideDetailResponse response = DestinationGuideDetailResponse.from(
                detail(guide("An overview.", "Food notes.", "Practical notes."),
                        List.of(area("shibuya")),
                        List.of(poi("tsukiji")),
                        List.of(transport("metro")),
                        List.of(app("suica"))),
                "en", NOW);

        assertThat(response.sourceRefs())
                .extracting(SourceRefSummaryResponse::fieldGroup)
                .containsExactly("overview", "food", "practical", "areas", "pois", "transport",
                        "local_app_pack");
    }

    /**
     * An uncurated section is not cited. A ref for prose that does not exist would assert that
     * something had been sourced when there is nothing there to source.
     */
    @Test
    void doesNotCiteAGuideSectionThatWasNeverWritten() {
        DestinationGuideDetailResponse response = DestinationGuideDetailResponse.from(
                detail(guide("An overview.", null, null),
                        List.of(), List.of(), List.of(), List.of()),
                "en", NOW);

        assertThat(response.sourceRefs())
                .extracting(SourceRefSummaryResponse::fieldGroup)
                .containsExactly("overview");
    }

    /**
     * Twelve POIs from one curated source produce one ref, not twelve — repetition would bury the
     * row that came from somewhere else, which is the only row a reader needs to notice.
     */
    @Test
    void collapsesOneSourceRepeatedAcrossAFieldGroup() {
        DestinationGuideDetailResponse response = DestinationGuideDetailResponse.from(
                detail(null, List.of(),
                        List.of(poi("tsukiji"), poi("senso-ji"), poi("shinjuku-gyoen")),
                        List.of(), List.of()),
                "en", NOW);

        assertThat(response.sourceRefs()).hasSize(1);
        assertThat(response.sourceRefs().get(0).fieldGroup()).isEqualTo("pois");
    }

    /**
     * The same source legitimately backs the narrative and the POI list. Keying the dedup on the
     * ref alone would leave one of the two uncited.
     */
    @Test
    void keepsOneSourceOncePerFieldGroupItBacks() {
        DestinationGuideDetailResponse response = DestinationGuideDetailResponse.from(
                detail(guide("An overview.", null, null), List.of(), List.of(poi("tsukiji")),
                        List.of(), List.of()),
                "en", NOW);

        assertThat(response.sourceRefs())
                .extracting(SourceRefSummaryResponse::sourceRef)
                .containsExactly("wikivoyage:tokyo", "wikivoyage:tokyo");
        assertThat(response.sourceRefs())
                .extracting(SourceRefSummaryResponse::fieldGroup)
                .containsExactly("overview", "pois");
    }

    /**
     * ADR 010 §6. A POI carries the 90-day TTL and the guide narrative 730, so one page can hold a
     * fresh narrative beside a stale opening time — which is exactly why the flag is per ref and not
     * per response.
     */
    @Test
    void flagsTheStaleRowWithoutCondemningTheFreshOneBesideIt() {
        Instant recent = NOW.minus(Duration.ofDays(100));
        DestinationGuideDetailResponse response = DestinationGuideDetailResponse.from(
                detail(guide("An overview.", null, null, provenance("wikivoyage:tokyo", recent)),
                        List.of(), List.of(poi("tsukiji", provenance("osm:tokyo", recent))),
                        List.of(), List.of()),
                "en", NOW);

        SourceRefSummaryResponse narrative = refFor(response, "overview");
        SourceRefSummaryResponse poi = refFor(response, "pois");

        assertThat(narrative.stale()).as("100 days against a 730-day TTL").isFalse();
        assertThat(poi.stale()).as("100 days against a 90-day TTL").isTrue();
    }

    /** ADR 010 §3 — a sample row announces itself rather than blending in. */
    @Test
    void marksSampleRowsAndRaisesThePageFlagWhenAnyRowIsSample() {
        DestinationGuideDetailResponse response = DestinationGuideDetailResponse.from(
                detail(guide("An overview.", null, null,
                                new KnowledgeProvenance(KnowledgeProvenance.SAMPLE_SOURCE_REF,
                                        "Sample seed", KnowledgeLicence.SAMPLE_DATA,
                                        "Sample data — not a real source", null, TrustTier.SAMPLE,
                                        NOW)),
                        List.of(), List.of(), List.of(), List.of()),
                "en", NOW);

        assertThat(response.sampleData()).isTrue();
        assertThat(refFor(response, "overview").sampleData()).isTrue();
    }

    @Test
    void doesNotRaiseTheSampleFlagForACuratedPage() {
        DestinationGuideDetailResponse response = DestinationGuideDetailResponse.from(
                detail(guide("An overview.", null, null), List.of(), List.of(), List.of(),
                        List.of()),
                "en", NOW);

        assertThat(response.sampleData()).isFalse();
    }

    /**
     * ADR 010 §2. The licence requires attribution, the domain refuses to exist without the text,
     * and the wire is where the obligation is actually discharged.
     */
    @Test
    void publishesAttributionForALicenceThatDemandsIt() {
        DestinationGuideDetailResponse response = DestinationGuideDetailResponse.from(
                detail(guide("An overview.", null, null), List.of(), List.of(), List.of(),
                        List.of()),
                "en", NOW);

        assertThat(refFor(response, "overview").attribution())
                .isEqualTo("© Wikivoyage contributors, CC BY-SA 4.0");
    }

    /** A guide that was never curated cites nothing rather than citing itself. */
    @Test
    void citesNothingWhenTheLocaleWasNeverCurated() {
        DestinationGuideDetailResponse response = DestinationGuideDetailResponse.from(
                detail(null, List.of(), List.of(), List.of(), List.of()), "ms", NOW);

        assertThat(response.sourceRefs()).isEmpty();
        assertThat(response.sampleData()).isFalse();
    }

    // ---------------------------------------------------------------------------------------

    private static SourceRefSummaryResponse refFor(
            DestinationGuideDetailResponse response, String fieldGroup) {
        return response.sourceRefs().stream()
                .filter(ref -> ref.fieldGroup().equals(fieldGroup))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no ref for field group " + fieldGroup));
    }

    private static DestinationGuideDetail detail(
            DestinationGuide guide, List<DestinationArea> areas, List<Poi> pois,
            List<TransportMode> modes, List<TravelApp> apps) {
        return new DestinationGuideDetail(
                TOKYO, Optional.ofNullable(guide), areas, pois, modes, apps);
    }

    private static KnowledgeProvenance provenance(String sourceRef, Instant retrievedAt) {
        return new KnowledgeProvenance(sourceRef, "Wikivoyage", KnowledgeLicence.CC_BY_SA_4_0,
                "© Wikivoyage contributors, CC BY-SA 4.0", "https://wikivoyage.org/Tokyo",
                TrustTier.COMMUNITY, retrievedAt);
    }

    private static DestinationGuide guide(String overview, String food, String practical) {
        return guide(overview, food, practical, provenance("wikivoyage:tokyo", NOW));
    }

    private static DestinationGuide guide(
            String overview, String food, String practical, KnowledgeProvenance provenance) {
        return new DestinationGuide(UUID.randomUUID(), DESTINATION_ID, "en", overview, food,
                practical, provenance, 0);
    }

    private static DestinationArea area(String slug) {
        return new DestinationArea(UUID.randomUUID(), DESTINATION_ID, slug, slug, null, null, null,
                provenance("wikivoyage:tokyo", NOW));
    }

    private static Poi poi(String slug) {
        return poi(slug, provenance("wikivoyage:tokyo", NOW));
    }

    private static Poi poi(String slug, KnowledgeProvenance provenance) {
        return new Poi(UUID.randomUUID(), DESTINATION_ID, null, slug, slug, null,
                PoiCategory.FOOD, List.of(), "en", null, null, null, null, provenance, 0);
    }

    private static TransportMode transport(String slug) {
        return new TransportMode(UUID.randomUUID(), DESTINATION_ID, slug, slug,
                TransportKind.METRO, null, null, true, provenance("wikivoyage:tokyo", NOW));
    }

    private static TravelApp app(String slug) {
        // At least one store link: the record refuses an app nobody can install.
        return new TravelApp(UUID.randomUUID(), "JP", slug, slug, TravelAppCategory.TRANSIT, null,
                "https://apps.apple.com/app/" + slug, null, provenance("wikivoyage:tokyo", NOW));
    }
}
