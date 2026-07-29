package com.travelplanner.domain.enums;

/**
 * The licence a fact arrived under (ADR 010 §2).
 *
 * <p>Licensing is a product constraint here, not a legal footnote. Two of these carry obligations
 * that reach the screen: attribution must be displayed wherever derived content is, and CC BY-SA's
 * share-alike term propagates into guide text derived from it. A licence the UI cannot honour is a
 * licence the row may not carry.
 */
public enum KnowledgeLicence {

    /** Wikivoyage. Attribution <em>and</em> share-alike on derived guide text. */
    CC_BY_SA_4_0(true, true),

    /** OpenStreetMap / Nominatim. Attribution on geometry and place data. */
    ODBL(true, false),

    /** A tourism board or operator's own terms — facts with citation. */
    OPERATOR_TERMS(true, false),

    /**
     * The reserved licence for sample rows. Attribution is still required, because ADR 010 §3
     * requires sample data to announce itself rather than blend in.
     */
    SAMPLE_DATA(true, false),

    /**
     * Google Places and anything else that may not be stored.
     *
     * <p>Present in the vocabulary <em>because</em> it is forbidden: a value that cannot be named
     * cannot be rejected with a useful message, and "we do not persist this" is easier to enforce
     * when the rule has somewhere to live. {@code ck_knowledge_source_not_forbidden} rejects it at
     * the database level too, so the rule survives somebody writing a new adapter.
     */
    PROPRIETARY_FORBIDDEN(false, false);

    private final boolean requiresAttribution;
    private final boolean shareAlike;

    KnowledgeLicence(boolean requiresAttribution, boolean shareAlike) {
        this.requiresAttribution = requiresAttribution;
        this.shareAlike = shareAlike;
    }

    /** Whether a page displaying content under this licence must show its attribution string. */
    public boolean requiresAttribution() {
        return requiresAttribution;
    }

    /**
     * Whether derived text inherits this licence.
     *
     * <p>True only for CC BY-SA. It matters because a guide rewritten from a Wikivoyage article is
     * a derivative work, so the obligation travels with the rewrite rather than staying with the
     * source row.
     */
    public boolean isShareAlike() {
        return shareAlike;
    }

    /** ADR 010 §2: whether a fact under this licence may be stored in the TKB at all. */
    public boolean isPersistable() {
        return this != PROPRIETARY_FORBIDDEN;
    }
}
