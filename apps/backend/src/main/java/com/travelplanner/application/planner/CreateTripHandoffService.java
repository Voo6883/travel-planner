package com.travelplanner.application.planner;

import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.exception.ConversationNotFoundException;
import com.travelplanner.domain.model.Conversation;
import com.travelplanner.domain.model.Message;
import com.travelplanner.domain.model.PlannerSession;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.port.ConversationRepositoryPort;
import com.travelplanner.domain.port.TripBriefRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Transactional handoff from a pre-trip planner conversation to a durable trip conversation. */
@Service
@RequiresDatabase
public class CreateTripHandoffService {

    private final ConversationRepositoryPort conversations;
    private final TripRepositoryPort trips;
    private final TripBriefRepositoryPort briefs;

    public CreateTripHandoffService(ConversationRepositoryPort conversations, TripRepositoryPort trips,
            TripBriefRepositoryPort briefs) {
        this.conversations = conversations;
        this.trips = trips;
        this.briefs = briefs;
    }

    @TransactionalWrite
    public Result createTrip(Command command, UserContext user) {
        Conversation conversation = conversations.findConversationByIdAndUserId(
                command.conversationId(), user.userId()).orElseThrow(ConversationNotFoundException::new);
        conversation.requireAppendable();
        if (conversation.tripIfPresent().isPresent()) {
            return Result.existing(conversation.tripIfPresent().orElseThrow());
        }

        PlannerSession session = requireOpenPlannerSession(conversation, user);
        Instant now = Instant.now();
        Trip trip = trips.save(Trip.create(user.userId(), TripNamer.nameFor(command.args()), now));
        briefs.save(TripBrief.createFor(trip.id(), now));
        conversations.saveConversation(conversation.linkToTrip(trip.id(), now));
        conversations.savePlannerSession(session.end(now));
        appendHandoffMessages(command, user, payloadFor(trip.id()));
        return Result.created(trip.id(), payloadFor(trip.id()));
    }

    private PlannerSession requireOpenPlannerSession(Conversation conversation, UserContext user) {
        UUID sessionId = conversation.plannerSessionIfPresent().orElseThrow(ConversationNotFoundException::new);
        PlannerSession session = conversations.findOpenPlannerSession(user.userId())
                .filter(open -> open.id().equals(sessionId))
                .orElseThrow(ConversationNotFoundException::new);
        if (!session.isOwnedBy(user.userId())) {
            throw new ConversationNotFoundException();
        }
        return session;
    }

    private void appendHandoffMessages(Command command, UserContext user, String payload) {
        Instant now = Instant.now();
        UUID conversationId = command.conversationId();
        long toolCallSeq = conversations.allocateSequence(conversationId, user.userId());
        conversations.appendMessage(Message.toolCall(conversationId, toolCallSeq, command.toolCallId(),
                PlannerTools.CREATE_TRIP, command.args().inputJson(), now));
        long toolResultSeq = conversations.allocateSequence(conversationId, user.userId());
        conversations.appendMessage(Message.toolResult(conversationId, toolResultSeq, command.toolCallId(),
                payload, now));
        long eventSeq = conversations.allocateSequence(conversationId, user.userId());
        conversations.appendMessage(Message.lifecycleEvent(conversationId, eventSeq, payload, now));
        touch(conversationId, user, now);
    }

    private void touch(UUID conversationId, UserContext user, Instant now) {
        conversations.findConversationByIdAndUserId(conversationId, user.userId())
                .ifPresent(conversation -> conversations.saveConversation(conversation.touchLastMessageAt(now)));
    }

    private static String payloadFor(UUID tripId) {
        return "{\"trip_id\":\"" + tripId + "\"}";
    }

    public record Command(UUID conversationId, String toolCallId, CreateTripArgs args) {
    }

    public record Result(UUID tripId, String payloadJson) {

        private static Result created(UUID tripId, String payloadJson) {
            return new Result(tripId, payloadJson);
        }

        private static Result existing(UUID tripId) {
            return new Result(tripId, payloadFor(tripId));
        }
    }
}
