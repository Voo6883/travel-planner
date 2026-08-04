package com.travelplanner.application.tripchat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.application.trip.AnswerClarificationCommand;
import com.travelplanner.application.trip.SaveTripBriefCommand;
import com.travelplanner.application.trip.TripAccess;
import com.travelplanner.application.trip.TripBriefService;
import com.travelplanner.application.trip.TripBriefView;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.ClarificationQuestion;
import com.travelplanner.domain.model.Message;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.port.ConversationRepositoryPort;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Executes the trip-brief intake tools against the deterministic C1 services (task 22, UC-C5-01/02).
 *
 * <p><strong>The model chooses the tool; this class decides whether it may run.</strong> Both
 * methods gate on {@code trip.status} <em>before</em> any state changes, so a status-gate refusal is
 * a {@code validation_failed} that mutates nothing — the trip is exactly as it was, and the model is
 * told why (task 22: "Status-gate rejection returns typed tool/error events and does not mutate
 * state"). The mutation itself is delegated to {@link TripBriefService}, which re-validates every
 * value and enforces the ADR 008 optimistic lock, so the tool is never a second, weaker way to
 * write the brief.
 *
 * <p>No LLM or HTTP call happens here — that is the orchestrator's job, outside this transaction
 * (PLAN: "no LLM/HTTP inside {@code @Transactional}"). Each method is one short write: gate, save,
 * append the two audit rows, return the fresh view.
 */
@Service
@RequiresDatabase
public class TripChatToolService {

    private final TripBriefService briefs;
    private final TripAccess access;
    private final ConversationRepositoryPort conversations;
    private final ObjectMapper objectMapper;

    public TripChatToolService(TripBriefService briefs, TripAccess access,
            ConversationRepositoryPort conversations, ObjectMapper objectMapper) {
        this.briefs = briefs;
        this.access = access;
        this.conversations = conversations;
        this.objectMapper = objectMapper;
    }

    /**
     * Merges the stated fields onto the stored brief and saves.
     *
     * @throws com.travelplanner.domain.exception.VersionConflictException on a stale
     *         {@code expected_version} — the agent-versus-form race ADR 008 §4 exists for
     * @throws com.travelplanner.domain.exception.DestinationNotCoveredException when a named slug is
     *         not curated
     * @throws ValidationFailedException on an archived trip, a disallowed status, or a bad value
     */
    @TransactionalWrite
    public Result applyUpdate(Context context, UpdateTripBriefArgs args) {
        Trip trip = access.requireEditable(context.tripId(), context.user());
        requireStatusAllows(trip.status(), context.toolName());

        TripBriefView current = briefs.get(context.tripId(), context.user());
        TripBriefDetails merged = args.mergeOnto(current.brief().details());
        SaveTripBriefCommand command =
                new SaveTripBriefCommand(context.tripId(), args.expectedVersion(), merged);
        TripBriefView saved = briefs.save(command, context.user());

        String payload = payloadFor(context.tripId(), saved, args.appliedFields());
        appendToolMessages(context, args.inputJson(), payload);
        return new Result(payload, saved);
    }

    /**
     * Applies typed clarification answers and re-validates.
     *
     * @throws ValidationFailedException when the status is not {@code CLARIFICATION_NEEDED}, when an
     *         answer is not to an outstanding question, or when its type does not match
     * @throws com.travelplanner.domain.exception.VersionConflictException on a stale version
     */
    @TransactionalWrite
    public Result applyClarification(Context context, AnswerClarificationArgs args) {
        Trip trip = access.requireEditable(context.tripId(), context.user());
        requireStatusAllows(trip.status(), context.toolName());

        AnswerClarificationCommand command =
                new AnswerClarificationCommand(context.tripId(), args.expectedVersion(), args.answers());
        TripBriefView saved = briefs.answerClarification(command, context.user());

        String payload = payloadFor(context.tripId(), saved, args.answeredQuestionIds());
        appendToolMessages(context, args.inputJson(), payload);
        return new Result(payload, saved);
    }

    private static void requireStatusAllows(TripStatus status, String toolName) {
        if (!TripChatTools.isAllowed(status, toolName)) {
            throw ValidationFailedException.field("status",
                    toolName + " is not available while the trip is " + status);
        }
    }

    /**
     * Records the model's request and the server's result in the thread, so a reload renders the
     * exchange. Not a {@code lifecycle_event}: a brief edit is an ordinary tool round, and marking
     * every field change as a lifecycle milestone would drown the genuine ones.
     */
    private void appendToolMessages(Context context, String inputJson, String payload) {
        Instant now = Instant.now();
        UUID conversationId = context.conversationId();
        UUID userId = context.user().userId();
        long callSeq = conversations.allocateSequence(conversationId, userId);
        conversations.appendMessage(Message.toolCall(conversationId, callSeq, context.toolCallId(),
                context.toolName(), inputJson, now));
        long resultSeq = conversations.allocateSequence(conversationId, userId);
        conversations.appendMessage(Message.toolResult(conversationId, resultSeq, context.toolCallId(),
                payload, now));
        touch(conversationId, context.user(), now);
    }

    private void touch(UUID conversationId, UserContext user, Instant now) {
        conversations.findConversationByIdAndUserId(conversationId, user.userId())
                .ifPresent(conversation -> conversations.saveConversation(conversation.touchLastMessageAt(now)));
    }

    private String payloadFor(UUID tripId, TripBriefView view, List<String> applied) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trip_id", tripId.toString());
        body.put("status", view.status().name());
        body.put("version", view.brief().version());
        body.put("outstanding_question_ids", outstandingIds(view));
        body.put("applied", applied);
        return serialise(body);
    }

    private static List<String> outstandingIds(TripBriefView view) {
        return view.clarification().questions().stream()
                .map(ClarificationQuestion::id)
                .toList();
    }

    private String serialise(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not serialise a trip-chat tool result", failure);
        }
    }

    /**
     * The correlation a tool execution needs, bundled so the two entry points stay within the
     * ≤3-parameter rule. {@code toolName} rides along because the audit row records which tool was
     * invoked, and the status gate names it in its refusal.
     */
    public record Context(UUID conversationId, UUID tripId, String toolCallId, String toolName,
            UserContext user) {
    }

    /** The serialised tool result the model sees, and the fresh view the orchestrator navigates on. */
    public record Result(String payloadJson, TripBriefView view) {
    }
}
