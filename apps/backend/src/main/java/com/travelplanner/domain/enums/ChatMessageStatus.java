package com.travelplanner.domain.enums;

/**
 * How a stored message ended — or that it has not ended yet.
 *
 * <p>This enum is the tasks/20 requirement "partial assistant messages must be explicitly marked or
 * safely discarded" expressed as a type. ADR 007 chose marking over discarding: "Partial assistant
 * message persisted with {@code status=interrupted}; never silently discarded", because the common
 * mobile case is a transient network loss part-way through a long answer, and dropping the text the
 * user already watched arrive is worse than showing it labelled as cut short.
 *
 * <p>The names are the persisted values ({@code message.status varchar} with
 * {@code ck_message_status}); {@code MigrationContractTest} asserts the set.
 */
public enum ChatMessageStatus {

    /**
     * Still being written. The only status whose row may change, and the only one with a null
     * {@code completed_at} ({@code ck_message_completed_at_matches_status}).
     */
    STREAMING,

    /** Finished normally — the model reached a stop reason (ADR 007 {@code Done}). */
    COMPLETE,

    /**
     * Cut short by a client disconnect or an explicit cancel. The content is real but partial, and
     * the UI must say so rather than presenting it as a finished answer.
     */
    INTERRUPTED,

    /** Ended on an ADR 007 {@code StreamError}. Whatever text arrived first is kept. */
    FAILED;

    /** True once the row can no longer change. */
    public boolean isTerminal() {
        return this != STREAMING;
    }

    /**
     * True when the content is known to be incomplete. Distinct from {@link #isTerminal()}: a
     * {@code COMPLETE} message is terminal and whole, an {@code INTERRUPTED} one is terminal and
     * not — and only the caller that knows the difference can render it honestly.
     */
    public boolean isPartial() {
        return this == INTERRUPTED || this == FAILED;
    }
}
