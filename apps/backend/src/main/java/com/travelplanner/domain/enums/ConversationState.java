package com.travelplanner.domain.enums;

/**
 * Whether a conversation still accepts messages (tasks/20 "archived/read-only behavior").
 *
 * <p>Deliberately the same shape as {@link TripStatus#isReadOnly()}: an archived thread is
 * view-only for every actor, the agent included. History still loads — archiving is not deletion,
 * and a user who archives a trip has not asked to lose what was said about it.
 *
 * <p>The names are the persisted values ({@code conversation.state varchar} with
 * {@code ck_conversation_state}); {@code MigrationContractTest} asserts the set.
 */
public enum ConversationState {

    /** Accepts new messages. */
    ACTIVE,

    /** Read-only. History loads; nothing appends. */
    ARCHIVED;

    public boolean isReadOnly() {
        return this == ARCHIVED;
    }
}
