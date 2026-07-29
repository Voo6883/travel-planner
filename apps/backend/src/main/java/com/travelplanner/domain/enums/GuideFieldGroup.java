package com.travelplanner.domain.enums;

/**
 * The three parts of a guide that embed separately (ADR 010 §5).
 *
 * <p>Chunking is per field group rather than per row because {@code destination_guide} is
 * heterogeneous: these answer different questions, and one vector spanning all three produces the
 * unusable centroid ADR 010 rejects.
 */
public enum GuideFieldGroup {
    OVERVIEW,
    FOOD,
    PRACTICAL
}
