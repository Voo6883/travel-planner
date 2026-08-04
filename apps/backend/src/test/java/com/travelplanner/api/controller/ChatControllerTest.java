package com.travelplanner.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelplanner.application.chat.ChatHistoryPage;
import com.travelplanner.application.chat.ChatStreamEvent;
import com.travelplanner.application.chat.ChatTarget;
import com.travelplanner.application.chat.ChatTurn;
import com.travelplanner.application.chat.ChatTurnService;
import com.travelplanner.application.chat.ChatConversationService;
import com.travelplanner.application.chat.SendChatMessageCommand;
import com.travelplanner.application.page.PageQuery;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.enums.ChatMessageRole;
import com.travelplanner.domain.enums.ChatMessageStatus;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.exception.ConversationNotFoundException;
import com.travelplanner.domain.exception.TripNotFoundException;
import com.travelplanner.domain.model.Message;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

/**
 * The chat routing contract: the request body, the history envelope, the streaming response's
 * headers, and the refusals that must stay HTTP failures rather than becoming stream frames.
 *
 * <p>{@code addFilters = false} for the reason {@code TripControllerTest} gives: a
 * {@code @WebMvcTest} does not load the application's own {@code SecurityConfig}, so Boot's default
 * chain would answer every routing assertion with a 401. That these paths require a session is a
 * property of {@code SecurityConfig}'s {@code anyRequest().authenticated()}.
 */
@WebMvcTest(controllers = ChatController.class,
        properties = "spring.datasource.url=jdbc:postgresql://localhost:5432/unused")
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerTest {

    private static final UUID TRIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONVERSATION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID USER_MESSAGE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID ASSISTANT_MESSAGE_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID USER_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");
    private static final String BODY =
            "{\"client_message_id\":\"cmid-1\",\"content\":\"Kyoto in spring?\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatTurnService turns;

    @MockitoBean
    private ChatConversationService conversations;

    // ------------------------------------------------------------------------------------------
    // Streaming.
    // ------------------------------------------------------------------------------------------

    @Test
    void aPlannerSendStreamsTheFramesTheClientParses() throws Exception {
        when(turns.openTurn(any(), any())).thenReturn(turn());
        when(turns.stream(any())).thenReturn(Flux.just(
                ChatStreamEvent.MessageStart.complete(userMessage()),
                ChatStreamEvent.MessageStart.streaming(assistantMessage()),
                new ChatStreamEvent.TextDelta(ASSISTANT_MESSAGE_ID, "Kyoto"),
                new ChatStreamEvent.MessageEnd(ASSISTANT_MESSAGE_ID, ChatMessageStatus.COMPLETE, 2L),
                new ChatStreamEvent.Done("end_turn")));

        String body = streamBody(post("/api/v1/planner/chat/messages")
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY));

        assertContainsFrame(body, "message_start", "\"client_message_id\":\"cmid-1\"");
        assertContainsFrame(body, "text_delta", "\"text\":\"Kyoto\"");
        assertContainsFrame(body, "message_end", "\"status\":\"complete\"");
        assertContainsFrame(body, "done", "\"stop_reason\":\"end_turn\"");
    }

    @Test
    void theStreamingResponseIsUnbufferableAndUncacheable() throws Exception {
        // A CDN that buffers an SSE response shows the user nothing until the turn is over — the one
        // failure mode that makes streaming pointless while looking like it works.
        when(turns.openTurn(any(), any())).thenReturn(turn());
        when(turns.stream(any())).thenReturn(Flux.just(new ChatStreamEvent.Done("end_turn")));

        mockMvc.perform(post("/api/v1/planner/chat/messages")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Conversation-Id", CONVERSATION_ID.toString()));
    }

    @Test
    void theTripPathBindsItsTripIdIntoTheCommand() throws Exception {
        when(turns.openTurn(any(), any())).thenReturn(turn());
        when(turns.stream(any())).thenReturn(Flux.just(new ChatStreamEvent.Done("end_turn")));

        mockMvc.perform(post("/api/v1/trips/" + TRIP_ID + "/chat/messages")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(request().asyncStarted());

        ArgumentCaptor<SendChatMessageCommand> command =
                ArgumentCaptor.forClass(SendChatMessageCommand.class);
        verify(turns).openTurn(command.capture(), any());
        assertThat(command.getValue().target()).isEqualTo(ChatTarget.trip(TRIP_ID));
        assertThat(command.getValue().clientMessageId()).isEqualTo("cmid-1");
        assertThat(command.getValue().content()).isEqualTo("Kyoto in spring?");
    }

    @Test
    void aSendWithoutAClientMessageIdIsRejectedBeforeAnythingIsCommitted() throws Exception {
        // Without the retry key a dropped response can commit the same message twice, so this is a
        // 400 rather than a server-generated fallback — which would differ on the retry and
        // guarantee the duplicate it was meant to prevent.
        mockMvc.perform(post("/api/v1/planner/chat/messages")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Kyoto in spring?\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        verify(turns, never()).openTurn(any(), any());
    }

    @Test
    void anEmptyMessageIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/planner/chat/messages")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_message_id\":\"cmid-1\",\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void aRefusalStaysAnHttpFailureRatherThanBecomingAStreamFrame() throws Exception {
        // The turn is opened synchronously precisely so the §6.1 envelope can still travel as a
        // status. Once a Flux is returned the response has committed to 200 and it cannot.
        doThrow(new TripNotFoundException()).when(turns).openTurn(any(), any());

        mockMvc.perform(post("/api/v1/trips/" + TRIP_ID + "/chat/messages")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void aClientAcceptingOnlyTheStreamCannotBeToldWhyItsRequestWasRefused() throws Exception {
        // Why `chat-stream.ts` sends `Accept: text/event-stream, application/json`. Content
        // negotiation applies to the error response too: with the stream type alone there is no
        // converter that can write the §6.1 envelope, and the client gets a status with an empty
        // body — the one shape `apiErrorFromResponse` cannot turn into a message for the user.
        // Pinned as a test because it is invisible until a real failure happens in production.
        mockMvc.perform(post("/api/v1/planner/chat/messages")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Kyoto in spring?\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    // ------------------------------------------------------------------------------------------
    // History.
    // ------------------------------------------------------------------------------------------

    @Test
    void historyPublishesTheSharedPageEnvelopeAndTheConversationId() throws Exception {
        when(conversations.history(any(), any(), any(), any())).thenReturn(new ChatHistoryPage(
                0, 30, 2L, CONVERSATION_ID, List.of(userMessage(), assistantMessage())));

        mockMvc.perform(get("/api/v1/planner/chat/messages?page=0&page_size=30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.page_size").value(30))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.conversation_id").value(CONVERSATION_ID.toString()))
                .andExpect(jsonPath("$.items[0].message_id").value(USER_MESSAGE_ID.toString()))
                .andExpect(jsonPath("$.items[0].client_message_id").value("cmid-1"))
                .andExpect(jsonPath("$.items[0].created_at").exists());
    }

    @Test
    void historyPublishesRolesAndStatusesInLowerCase() throws Exception {
        // STATUS F-31: the client's defensive `toLowerCase()` exists only because this was unsettled.
        when(conversations.history(any(), any(), any(), any())).thenReturn(new ChatHistoryPage(
                0, 30, 2L, CONVERSATION_ID, List.of(userMessage(), assistantMessage())));

        mockMvc.perform(get("/api/v1/planner/chat/messages"))
                .andExpect(jsonPath("$.items[0].role").value("user"))
                .andExpect(jsonPath("$.items[0].status").value("complete"))
                .andExpect(jsonPath("$.items[1].role").value("assistant"))
                .andExpect(jsonPath("$.items[1].status").value("streaming"));
    }

    @Test
    void historyDefaultsToTheContractsPagination() throws Exception {
        when(conversations.history(any(), any(), any(), any()))
                .thenReturn(ChatHistoryPage.empty(0, PageQuery.DEFAULT_PAGE_SIZE));

        mockMvc.perform(get("/api/v1/planner/chat/messages")).andExpect(status().isOk());

        ArgumentCaptor<PageQuery> query = ArgumentCaptor.forClass(PageQuery.class);
        verify(conversations).history(any(), any(), query.capture(), any());
        assertThat(query.getValue().page()).isEqualTo(PageQuery.DEFAULT_PAGE);
        assertThat(query.getValue().pageSize()).isEqualTo(PageQuery.DEFAULT_PAGE_SIZE);
    }

    @Test
    void anOutOfRangePageSizeIsRejectedRatherThanClamped() throws Exception {
        // Silently serving 100 when the caller asked for 5000 makes their paging arithmetic wrong
        // in a way they cannot detect.
        mockMvc.perform(get("/api/v1/planner/chat/messages?page_size=5000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void aConversationThatIsNotTheCallersIsNotFound() throws Exception {
        when(conversations.history(any(), any(), any(), any()))
                .thenThrow(new ConversationNotFoundException());

        mockMvc.perform(get("/api/v1/planner/chat/messages?conversation_id=" + CONVERSATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void historyOnTheTripPathBindsTheTripId() throws Exception {
        when(conversations.history(any(), any(), any(), any()))
                .thenReturn(ChatHistoryPage.empty(0, 30));

        mockMvc.perform(get("/api/v1/trips/" + TRIP_ID + "/chat/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        ArgumentCaptor<ChatTarget> target = ArgumentCaptor.forClass(ChatTarget.class);
        verify(conversations).history(target.capture(), any(), any(), any());
        assertThat(target.getValue()).isEqualTo(ChatTarget.trip(TRIP_ID));
    }

    // ------------------------------------------------------------------------------------------

    private String streamBody(org.springframework.test.web.servlet.RequestBuilder request)
            throws Exception {
        MvcResult started = mockMvc.perform(request)
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk());
        return started.getResponse().getContentAsString();
    }

    private static void assertContainsFrame(String body, String eventName, String payloadFragment) {
        assertThat(body).contains("event:" + eventName).contains(payloadFragment);
    }

    private static ChatTurn turn() {
        return new ChatTurn(CONVERSATION_ID, ChatTarget.planner(), user(), userMessage(), assistantMessage(),
                Prompt.adHoc(List.of(PromptMessage.user("Kyoto in spring?"))));
    }

    private static UserContext user() {
        return UserContext.of(USER_ID, "traveller@example.com", Role.USER);
    }

    private static Message userMessage() {
        return new Message(USER_MESSAGE_ID, CONVERSATION_ID, 1L, ChatMessageRole.USER,
                ChatMessageStatus.COMPLETE, "Kyoto in spring?", "cmid-1", null, null, NOW, NOW, NOW);
    }

    private static Message assistantMessage() {
        return new Message(ASSISTANT_MESSAGE_ID, CONVERSATION_ID, 2L, ChatMessageRole.ASSISTANT,
                ChatMessageStatus.STREAMING, "", null, null, null, NOW, NOW, null);
    }
}
