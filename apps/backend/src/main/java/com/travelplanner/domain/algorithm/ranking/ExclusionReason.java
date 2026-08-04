package com.travelplanner.domain.algorithm.ranking;

/**
 * Why a candidate never entered the ranked list (UC-C2-05). Structured — never prose only.
 */
public enum ExclusionReason {

    /** ADR 010 §4 / {@code CoverageLevel}: only FULL destinations may be ranked. */
    NOT_RANKING_ELIGIBLE,

    /** Estimated trip cost exceeds the brief budget in the same currency. */
    BUDGET_EXCEEDED,

    /** Brief has dates but the candidate has no seasonality rows for those months. */
    NO_SEASONALITY_FOR_DATES,

    /** Price observations are not in the brief budget currency. */
    CURRENCY_MISMATCH,

    /** Scored, but confidence fell below the ranker's floor. */
    LOW_CONFIDENCE
}
