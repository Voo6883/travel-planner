package com.travelplanner.domain.enums;

/**
 * Whether a conversation belongs to a trip yet (PLAN §3.2).
 *
 * <p>The names are the persisted values ({@code conversation.scope varchar} with
 * {@code ck_conversation_scope}) and the wire values, which is why {@code MigrationContractTest}
 * asserts this set against the CHECK constraint rather than trusting review to catch a rename.
 *
 * <p>Only one transition exists — {@link #PLANNER} to {@link #TRIP}, at the {@code create_trip}
 * handoff. A trip conversation never becomes a planner conversation again: its messages are the
 * trip's history, and detaching them would leave the trip with no record of how it was created.
 */
public enum ConversationScope {

    /** Pre-trip planner chat. Hangs off a {@code planner_session}; {@code trip_id} is null. */
    PLANNER,

    /** The trip's one persistent conversation, from {@code create_trip} until archived. */
    TRIP;

    /** True when a conversation in this scope must carry a {@code tripId}. */
    public boolean requiresTrip() {
        return this == TRIP;
    }
}
