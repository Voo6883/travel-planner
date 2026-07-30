package com.travelplanner.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.api.dto.chat.ChatEventEncoder;
import com.travelplanner.api.dto.chat.ChatHistoryPageResponse;
import com.travelplanner.api.dto.chat.SendChatMessageRequest;
import com.travelplanner.api.filter.RequestIdFilter;
import com.travelplanner.application.chat.ChatConversationService;
import com.travelplanner.application.chat.ChatReplay;
import com.travelplanner.application.chat.ChatTarget;
import com.travelplanner.application.chat.ChatTurn;
import com.travelplanner.application.chat.ChatTurnService;
import com.travelplanner.application.chat.SendChatMessageCommand;
import com.travelplanner.application.page.PageQuery;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.valueobject.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * The two chat surfaces over HTTP (PLAN §3.2, ADR 007).
 *
 * <p>{@code POST} streams a turn, {@code GET} reads history — same path, different verb, which is
 * why the frontend's {@code chatMessagesPath()} is a single URL builder with nothing else in the app
 * allowed to assemble one.
 *
 * <h2>POST-SSE, not {@code EventSource}</h2>
 *
 * <p>ADR 007: {@code EventSource} is GET-only and cannot set headers, so it can carry neither the
 * message body nor the CSRF token ADR 006 requires. The transport is therefore {@code POST} with
 * {@code Accept: text/event-stream}, and {@code produces} below makes that {@code Accept} header
 * load-bearing — a caller that asks for JSON gets {@code 406} rather than a stream it cannot read.
 *
 * <h2>Why the turn is opened before the Flux is returned</h2>
 *
 * <p>{@link ChatTurnService#openTurn} runs synchronously, on the request thread, and commits every
 * write the turn needs. Only then does the method return a publisher. That ordering is what keeps
 * refusals answerable as HTTP: "this thread is archived" is a {@code 400} with the §6.1 envelope in
 * the body, not an {@code event: error} on a {@code 200} that should never have been sent. Once the
 * {@code Flux} is returned the response has committed and the envelope can only travel in-band.
 *
 * <h2>Routing only</h2>
 *
 * <p>No transaction is declared here and none may be (ArchUnit
 * {@code controllersAreNotTransactional}); the service layer owns them, and for chat it owns them
 * with particular care — see {@link ChatConversationService} for why a transaction may never span
 * the stream.
 */
@RestController
@RequestMapping("/api/v1")
@RequiresDatabase
public class ChatController {

    /**
     * Defeats proxy response buffering (ADR 007). nginx and several CDNs buffer an upstream response
     * by default, which for SSE means the user sees nothing at all until the turn is over — the one
     * failure mode that makes streaming pointless while looking like it works.
     */
    private static final String ACCEL_BUFFERING_HEADER = "X-Accel-Buffering";

    /**
     * Published so a client can address the conversation the first planner turn just created,
     * without waiting for a history call. It cannot go in the body: the body is a stream.
     */
    private static final String CONVERSATION_ID_HEADER = "X-Conversation-Id";

    /**
     * ADR 007's resume cursor: the {@code seq} of the last frame the client applied.
     *
     * <p>Read here rather than declared as a {@code @RequestHeader} parameter because it is optional
     * <em>and</em> malformed input must not become a 400. A proxy or a buggy client can put anything in
     * it, and a resume hint that cannot be parsed is not worth refusing a message over — it degrades to
     * "replay everything the client is missing", which is correct and merely less efficient.
     */
    private static final String LAST_EVENT_ID_HEADER = "Last-Event-ID";

    private final ChatTurnService turns;
    private final ChatConversationService conversations;
    private final ObjectMapper objectMapper;

    public ChatController(ChatTurnService turns, ChatConversationService conversations,
            ObjectMapper objectMapper) {
        this.turns = turns;
        this.conversations = conversations;
        this.objectMapper = objectMapper;
    }

    /** UC-C5-08 — the pre-trip planner turn. Creates the conversation on the first send. */
    @PostMapping(path = "/planner/chat/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendPlannerMessage(
            @Valid @RequestBody SendChatMessageRequest request,
            @AuthenticationPrincipal UserContext caller,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        return stream(ChatTarget.planner(), request, caller, httpRequest, response);
    }

    /** UC-C5-09 — a turn in the trip's one persistent conversation. */
    @PostMapping(path = "/trips/{tripId}/chat/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendTripMessage(@PathVariable UUID tripId,
            @Valid @RequestBody SendChatMessageRequest request,
            @AuthenticationPrincipal UserContext caller,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        return stream(ChatTarget.trip(tripId), request, caller, httpRequest, response);
    }

    /** One page of planner history. Page 0 is the newest page; see {@link ChatHistoryPageResponse}. */
    @GetMapping("/planner/chat/messages")
    public ChatHistoryPageResponse plannerHistory(@RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "conversation_id", required = false) UUID conversationId,
            @AuthenticationPrincipal UserContext caller) {
        return history(ChatTarget.planner(), conversationId, page, pageSize, caller);
    }

    /** One page of a trip's history. {@code 404 not_found} for a trip that is not the caller's. */
    @GetMapping("/trips/{tripId}/chat/messages")
    public ChatHistoryPageResponse tripHistory(@PathVariable UUID tripId,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "conversation_id", required = false) UUID conversationId,
            @AuthenticationPrincipal UserContext caller) {
        return history(ChatTarget.trip(tripId), conversationId, page, pageSize, caller);
    }

    private Flux<ServerSentEvent<String>> stream(ChatTarget target, SendChatMessageRequest request,
            UserContext caller, HttpServletRequest httpRequest, HttpServletResponse response) {

        SendChatMessageCommand command = request.toCommand(target);

        // Resume is tried first, and it is the only branch that does not write. A reconnect whose turn
        // already finished is answered from the database — no provider call, and the same answer the
        // user had started reading rather than a second, different one (ADR 007 resume; F-39).
        Optional<ChatReplay> replay = turns.replay(command, caller, lastEventId(httpRequest));
        if (replay.isPresent()) {
            ChatEventEncoder encoder = openStream(response, replay.get().conversationId());
            return Flux.fromIterable(replay.get().frames()).map(encoder::encode);
        }

        ChatTurn turn = turns.openTurn(command, caller);
        ChatEventEncoder encoder = openStream(response, turn.conversationId());
        return turns.stream(turn).map(encoder::encode);
    }

    /**
     * The three headers every stream carries, set before the body is returned.
     *
     * <p>Once the publisher is handed back the response is committed and a header set afterwards is
     * silently discarded — which is why this is a method rather than three lines repeated in two
     * branches that must not drift.
     */
    private ChatEventEncoder openStream(HttpServletResponse response, UUID conversationId) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(ACCEL_BUFFERING_HEADER, "no");
        response.setHeader(CONVERSATION_ID_HEADER, conversationId.toString());
        return new ChatEventEncoder(objectMapper, requestId(response));
    }

    /**
     * The resume cursor, or {@code null} when the header is absent or unusable.
     *
     * <p>Anything unparseable is treated as absent rather than as a 400. The header is a hint about
     * what the client already has; getting it wrong costs a few duplicate frames, which the frontend
     * reducer drops idempotently, and refusing the message instead would turn a proxy quirk into a
     * chat that cannot be used at all.
     */
    private static Long lastEventId(HttpServletRequest request) {
        String raw = request.getHeader(LAST_EVENT_ID_HEADER);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private ChatHistoryPageResponse history(ChatTarget target, UUID conversationId, Integer page,
            Integer pageSize, UserContext caller) {
        // `sort` is not accepted: history has exactly one order, `seq` ascending, and it is the only
        // order the schema can guarantee (V19's header records why `created_at` cannot order a
        // conversation). A sort parameter would publish a choice the endpoint does not have.
        PageQuery query = PageQuery.of(page, pageSize, null);
        return ChatHistoryPageResponse.from(conversations.history(target, conversationId, query, caller));
    }

    /**
     * The correlation id, captured on the request thread.
     *
     * <p>It cannot be read later from the MDC: frames are emitted on Reactor workers after the
     * request thread has moved on, and an MDC lookup there returns nothing — or, worse, another
     * request's id once the thread has been reused.
     */
    private static String requestId(HttpServletResponse response) {
        String fromContext = MDC.get(RequestIdFilter.MDC_KEY);
        return fromContext != null ? fromContext : response.getHeader(RequestIdFilter.HEADER);
    }
}
