package com.travelplanner.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The CI seed validator against the committed sample corpus (task 17). */
class SampleKnowledgeValidatorTest {

    @Test
    void committedSampleSeedPassesStructuralValidation() {
        assertThat(new SampleKnowledgeValidator().validateAll()).isEmpty();
    }
}
