package com.travelplanner.api.dto.chat;

import com.travelplanner.api.dto.page.PageMetadata;
import com.travelplanner.application.chat.ChatHistoryPage;
import java.util.List;
import java.util.UUID;

/**
 * The body of {@code GET} on both chat paths (STATUS <b>F-36</b>).
 *
 * <p>The shared {@code page}/{@code page_size}/{@code total} envelope of PLAN §6.1 — the same three
 * fields {@code AdminUserPageResponse} publishes, produced by the same {@link PageMetadata} and the
 * same {@code PageQuery} validation, so {@code page} is zero-based, {@code page_size} defaults to 20
 * and is rejected rather than clamped above 100. The frontend had assumed exactly this envelope with
 * a default of 30; the envelope was right, the default was not, and the contract's number wins.
 *
 * <p>Each list endpoint declares its own response record rather than sharing a generic
 * {@code PageResponse<T>}, for the reason {@link PageMetadata} spells out: OpenAPI 3.0 has no
 * generics, so a shared wrapper would degrade to {@code items: object} and erase the element type in
 * the generated client.
 *
 * <p><strong>The one field this envelope adds.</strong> {@code conversation_id} is null for a caller
 * who has no conversation on this surface yet — a new account opening the planner home. That is an
 * empty page rather than a {@code 404}, because "you have not started chatting" is an answer, and a
 * 404 would send the client into an error state on the happy path of a first visit.
 *
 * <p><strong>Page 0 is the newest page</strong> (see {@link ChatHistoryPage}); within a page the
 * order is {@code seq} ascending. Both facts are documented on the path in {@code openapi.yaml},
 * because the shape alone cannot express them.
 */
public record ChatHistoryPageResponse(int page, int pageSize, long total, UUID conversationId,
        List<ChatHistoryMessageResponse> items) {

    public static ChatHistoryPageResponse from(ChatHistoryPage page) {
        PageMetadata metadata = new PageMetadata(page.page(), page.pageSize(), page.total());
        return new ChatHistoryPageResponse(metadata.page(), metadata.pageSize(), metadata.total(),
                page.conversationId(),
                page.items().stream().map(ChatHistoryMessageResponse::from).toList());
    }
}
