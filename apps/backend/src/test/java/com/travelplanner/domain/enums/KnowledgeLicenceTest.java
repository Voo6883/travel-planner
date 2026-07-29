package com.travelplanner.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class KnowledgeLicenceTest {

    @Test
    void requiresAttributionForEveryLicenceThatMayActuallyBeStored() {
        // PROPRIETARY_FORBIDDEN is the only value with no attribution obligation, and only because
        // nothing under it ever reaches a screen to attribute.
        assertThat(KnowledgeLicence.values())
                .filteredOn(KnowledgeLicence::requiresAttribution)
                .containsExactly(
                        KnowledgeLicence.CC_BY_SA_4_0,
                        KnowledgeLicence.ODBL,
                        KnowledgeLicence.OPERATOR_TERMS,
                        KnowledgeLicence.SAMPLE_DATA);
    }

    @Test
    void treatsOnlyCcBySaAsShareAlike() {
        // Share-alike is what propagates into rewritten guide text, so widening this set silently
        // would relicense derived content.
        assertThat(KnowledgeLicence.values())
                .filteredOn(KnowledgeLicence::isShareAlike)
                .containsExactly(KnowledgeLicence.CC_BY_SA_4_0);
    }

    @Test
    void refusesPersistenceOnlyForTheProprietaryForbiddenLicence() {
        assertThat(KnowledgeLicence.values())
                .filteredOn(licence -> !licence.isPersistable())
                .containsExactly(KnowledgeLicence.PROPRIETARY_FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(KnowledgeLicence.class)
    void neverCombinesShareAlikeWithNoAttributionObligation(KnowledgeLicence licence) {
        // A derivative work that inherits the licence but names no source cannot satisfy either
        // term, so this combination must not exist for any value present or future.
        assertThat(licence.isShareAlike() && !licence.requiresAttribution()).isFalse();
    }
}
