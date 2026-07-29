package com.travelplanner.application.chat;

import com.travelplanner.domain.model.Message;
import java.util.List;
import java.util.UUID;

/**
 * One page of conversation history, oldest-first within the page.
 *
 * <p><strong>Page 0 is the newest page.</strong> A chat panel opens at the bottom and pages
 * backwards, so "page 1" means "the messages before the ones you can see", not "the second thing
 * that ever happened in this thread". That is what {@code loadOlderMessages()} in
 * {@code features/chat/hooks/use-chat-stream.ts} asks for, and it is why this endpoint pages from
 * the end while {@code GET /admin/users} pages from the start — the shared {@code page}/
 * {@code page_size}/{@code total} envelope is the same, the anchor is not, and the anchor is
 * documented on the path.
 *
 * <p>Within a page the order is {@code seq} ascending, always. {@code seq} is the only ordering the
 * schema guarantees: V19's header records that {@code created_at} is fixed for a whole transaction
 * in Postgres, so several rows of one turn share it byte for byte and sorting by it is not an
 * ordering at all.
 *
 * @param conversationId {@code null} when the caller has no conversation yet — a new account
 *        opening the planner home. An empty page is the right answer there, not a 404
 */
public record ChatHistoryPage(int page, int pageSize, long total, UUID conversationId,
        List<Message> items) {

    public ChatHistoryPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** The answer for a caller who has no conversation on this surface yet. */
    public static ChatHistoryPage empty(int page, int pageSize) {
        return new ChatHistoryPage(page, pageSize, 0L, null, List.of());
    }
}
