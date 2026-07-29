package com.travelplanner.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.enums.KnowledgeDataClass;
import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.TrustTier;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class KnowledgeProvenanceTest {

    private static final Instant POI_DETAILS_EXPIRY =
            KnowledgeFixtures.RETRIEVED_AT.plus(KnowledgeDataClass.POI_DETAILS.timeToLive());

    @Test
    void carriesTheCitationAndTheFetchTimeAlongsideTheFact() {
        KnowledgeProvenance provenance = KnowledgeFixtures.provenance();

        assertThat(provenance.sourceRef()).isEqualTo("wikivoyage:tokyo");
        assertThat(provenance.name()).isEqualTo("Wikivoyage");
        assertThat(provenance.licence()).isEqualTo(KnowledgeLicence.CC_BY_SA_4_0);
        assertThat(provenance.attributionText()).contains("CC BY-SA 4.0");
        assertThat(provenance.sourceUrl()).isEqualTo("https://en.wikivoyage.org/wiki/Tokyo");
        assertThat(provenance.trustTier()).isEqualTo(TrustTier.COMMUNITY);
        assertThat(provenance.retrievedAt()).isEqualTo(KnowledgeFixtures.RETRIEVED_AT);
        assertThat(provenance).isEqualTo(KnowledgeFixtures.provenance());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsASourceRefThatCitesNothing(String blank) {
        assertThatThrownBy(() -> provenanceWithSourceRef(blank))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceRef must not be blank");
    }

    @Test
    void rejectsABlankAttributionWhenTheLicenceDemandsOne() {
        // The obligation is on the UI, which needs a string to render. A licence that requires
        // attribution paired with nothing to attribute is a row that can never be displayed.
        assertThatThrownBy(() -> new KnowledgeProvenance("osm:tokyo", "OpenStreetMap",
                KnowledgeLicence.ODBL, "  ", null, TrustTier.COMMUNITY,
                KnowledgeFixtures.RETRIEVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires attribution");
    }

    @Test
    void refusesToBuildAProvenanceForALicenceThatMayNotBePersisted() {
        // ADR 010 §2. Failing at construction means an adapter never gets far enough to be
        // surprised by ck_knowledge_source_not_forbidden three layers down.
        assertThatThrownBy(() -> new KnowledgeProvenance("google:places", "Google Places",
                KnowledgeLicence.PROPRIETARY_FORBIDDEN, "", null, TrustTier.OFFICIAL,
                KnowledgeFixtures.RETRIEVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("may not be persisted");
    }

    @Test
    void rejectsTheSampleLicenceWhenTheTrustTierIsNotSample() {
        // Half the alignment rule: a stub row claiming COMMUNITY trust would be indistinguishable
        // from curated content everywhere downstream.
        assertThatThrownBy(() -> new KnowledgeProvenance(KnowledgeProvenance.SAMPLE_SOURCE_REF,
                "Sample data", KnowledgeLicence.SAMPLE_DATA, "Sample data — not a real source",
                null, TrustTier.COMMUNITY, KnowledgeFixtures.RETRIEVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SAMPLE_DATA and TrustTier.SAMPLE must be used together");
    }

    @Test
    void rejectsTheSampleTrustTierWhenTheLicenceIsNotSampleData() {
        // The other half. Without it a real Wikivoyage import could be filed under SAMPLE and get
        // quietly suppressed from the UI as if it were a stub.
        assertThatThrownBy(() -> new KnowledgeProvenance("osm:tokyo", "OpenStreetMap",
                KnowledgeLicence.ODBL, "© OpenStreetMap contributors", null, TrustTier.SAMPLE,
                KnowledgeFixtures.RETRIEVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SAMPLE_DATA and TrustTier.SAMPLE must be used together");
    }

    @Test
    void rejectsEveryRequiredFieldBeingAbsent() {
        assertThatThrownBy(() -> new KnowledgeProvenance(null, "Wikivoyage",
                KnowledgeLicence.ODBL, "text", null, TrustTier.COMMUNITY, Instant.EPOCH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KnowledgeProvenance("ref", null,
                KnowledgeLicence.ODBL, "text", null, TrustTier.COMMUNITY, Instant.EPOCH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KnowledgeProvenance("ref", "Wikivoyage",
                null, "text", null, TrustTier.COMMUNITY, Instant.EPOCH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KnowledgeProvenance("ref", "Wikivoyage",
                KnowledgeLicence.ODBL, null, null, TrustTier.COMMUNITY, Instant.EPOCH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KnowledgeProvenance("ref", "Wikivoyage",
                KnowledgeLicence.ODBL, "text", null, null, Instant.EPOCH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KnowledgeProvenance("ref", "Wikivoyage",
                KnowledgeLicence.ODBL, "text", null, TrustTier.COMMUNITY, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void announcesItselfAsSampleDataOnlyWhenTheTrustTierIsSample() {
        assertThat(KnowledgeFixtures.sampleProvenance().isSampleData()).isTrue();
        assertThat(KnowledgeFixtures.sampleProvenance().sourceRef())
                .isEqualTo(KnowledgeProvenance.SAMPLE_SOURCE_REF);
        assertThat(KnowledgeFixtures.sampleProvenance().sourceUrl())
                .as("sample data deliberately has nowhere real to point")
                .isNull();
        assertThat(KnowledgeFixtures.provenance().isSampleData()).isFalse();
    }

    @Test
    void isNotYetStaleTheInstantBeforeItsTimeToLiveElapses() {
        assertThat(KnowledgeFixtures.provenance()
                .isStaleAt(POI_DETAILS_EXPIRY.minusSeconds(1), KnowledgeDataClass.POI_DETAILS))
                .isFalse();
    }

    @Test
    void isNotYetStaleExactlyAtItsTimeToLiveBecauseTheBoundaryIsExclusive() {
        // retrievedAt + TTL is the last good instant, not the first bad one. Pinned here so a
        // later refactor to isAfter/!isBefore cannot silently shift the whole catalogue by a tick.
        assertThat(KnowledgeFixtures.provenance()
                .isStaleAt(POI_DETAILS_EXPIRY, KnowledgeDataClass.POI_DETAILS))
                .isFalse();
    }

    @Test
    void isStaleOnceItsTimeToLiveHasElapsed() {
        assertThat(KnowledgeFixtures.provenance()
                .isStaleAt(POI_DETAILS_EXPIRY.plusSeconds(1), KnowledgeDataClass.POI_DETAILS))
                .isTrue();
        assertThat(KnowledgeFixtures.provenance()
                .isStaleAt(POI_DETAILS_EXPIRY.plusSeconds(86_400), KnowledgeDataClass.POI_DETAILS))
                .isTrue();
    }

    @Test
    void answersStalenessPerDataClassRatherThanPerRow() {
        // The same fetch is expired as an opening time and perfectly current as guide narrative —
        // which is why staleness takes the data class as an argument instead of being a field.
        Instant sixMonthsLater = KnowledgeFixtures.RETRIEVED_AT.plus(Duration.ofDays(180));
        KnowledgeProvenance provenance = KnowledgeFixtures.provenance();

        assertThat(provenance.isStaleAt(sixMonthsLater, KnowledgeDataClass.POI_DETAILS)).isTrue();
        assertThat(provenance.isStaleAt(sixMonthsLater, KnowledgeDataClass.GUIDE_NARRATIVE)).isFalse();
    }

    @Test
    void refusesToAnswerStalenessWithoutAClockOrADataClass() {
        KnowledgeProvenance provenance = KnowledgeFixtures.provenance();

        assertThatThrownBy(() -> provenance.isStaleAt(null, KnowledgeDataClass.POI_DETAILS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> provenance.isStaleAt(Instant.EPOCH, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static KnowledgeProvenance provenanceWithSourceRef(String sourceRef) {
        return new KnowledgeProvenance(sourceRef, "Wikivoyage", KnowledgeLicence.CC_BY_SA_4_0,
                "Wikivoyage contributors", null, TrustTier.COMMUNITY, KnowledgeFixtures.RETRIEVED_AT);
    }
}
