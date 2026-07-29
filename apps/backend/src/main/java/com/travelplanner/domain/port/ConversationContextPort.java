package com.travelplanner.domain.port;

import com.travelplanner.domain.model.ConversationContext;
import java.util.UUID;

/**
 * Builds the model input for one turn from a conversation's stored history (tasks/20:
 * "Conversation context assembly interface, but no C1/C2/C3 tools yet").
 *
 * <p><strong>Interface only. There is no implementation in tasks/20, and adding one here would be
 * out of scope.</strong> The orchestrators that own the turn — {@code PlannerChatOrchestrator} and
 * {@code TripChatOrchestrator} (PLAN §3.2) — arrive with tasks 21/22 and implement this. What
 * tasks/20 owes them is a stable shape to implement against, so the persistence layer and the
 * agent layer can be built in either order.
 *
 * <p><strong>Why this is a port and not a service.</strong> Assembly needs facts the domain cannot
 * reach on its own: the stored history, and later {@code trip.status}, the {@code TripBrief}, and
 * the research/itinerary snapshot (PLAN §3.2 "Context"). Expressing it as a port keeps the
 * dependency pointing inward — the agent layer depends on this interface, not the other way round
 * — which is the same reason {@code LlmPort} lives here rather than in {@code ai/}.
 *
 * <p><strong>What an implementation must guarantee.</strong> These are contract, not advice, and
 * the tests of any future implementation should assert them:
 *
 * <ul>
 *   <li>{@code ConversationContext.history()} is oldest first — the order a model must receive it
 *       in, and the order {@code uq_message_conversation_seq} already produces.</li>
 *   <li>The window is bounded. A thread grows without limit and a token budget does not, so an
 *       implementation truncates and reports it through
 *       {@link ConversationContext#truncated()} rather than failing the turn or silently
 *       forgetting.</li>
 *   <li><strong>No hidden chain-of-thought.</strong> The context is assembled from stored
 *       {@code message} rows, whose {@code content} is user-visible text only. An implementation
 *       must not reconstruct, infer, or carry forward provider reasoning traces — that is the
 *       tasks/20 Definition of Done, and this is the boundary where it would be easiest to break
 *       by accident.</li>
 *   <li>Ownership is enforced, not assumed. The {@code userId} argument is not decoration: an
 *       implementation filters on it (PLAN §4.0.2-L), because a context built for the wrong user
 *       would be leaked into a model prompt and then into an answer.</li>
 * </ul>
 */
public interface ConversationContextPort {

    /**
     * Assembles the context for the next turn of a conversation.
     *
     * @param maxMessages the most recent messages to include, newest-bounded and oldest-first on
     *        return. A cap rather than a token budget because tokenisation is provider-specific and
     *        lives behind {@code LlmPort}; a caller that needs a tighter bound passes a smaller cap.
     * @return the assembled context, with {@code truncated} set when older messages were left out
     * @throws com.travelplanner.domain.exception.ValidationFailedException when the conversation
     *         does not exist for this user
     */
    ConversationContext assembleFor(UUID conversationId, UUID userId, int maxMessages);
}
