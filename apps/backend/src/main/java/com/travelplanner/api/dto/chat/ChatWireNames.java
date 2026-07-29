package com.travelplanner.api.dto.chat;

import com.travelplanner.domain.enums.ChatMessageRole;
import com.travelplanner.domain.enums.ChatMessageStatus;
import java.util.Locale;

/**
 * The single place a chat enum becomes a wire value (STATUS <b>F-31</b>).
 *
 * <h2>The problem this settles</h2>
 *
 * <p>{@link ChatMessageRole} and {@link ChatMessageStatus} are {@code UPPER_SNAKE} in Java, because
 * that is what a Java enum constant is and because those exact strings are the persisted values that
 * {@code ck_message_role} and {@code ck_message_status} enforce. ADR 007's wire vocabulary is
 * lower-case snake: {@code message_start}, {@code text_delta}, {@code role: "user"},
 * {@code status: "interrupted"}. Before this class existed the two disagreed and the frontend
 * papered over it, lower-casing defensively in {@code chat-events.ts} and again in {@code chat-api.ts}
 * — two components each compensating for a contract neither of them owned.
 *
 * <h2>The rule</h2>
 *
 * <p><b>Java stays upper-case; the wire is lower-case; the conversion happens here and nowhere
 * else.</b> Renaming the domain constants was never an option — they are the values in the database
 * and in two CHECK constraints — and teaching Jackson to lower-case every enum globally would have
 * silently rewritten {@code roles: ["ADMIN"]} and {@code linked_providers: ["GITHUB"]}, which are
 * published upper-case and consumed that way by the generated client. So the conversion is scoped to
 * the chat DTOs, which is the only surface ADR 007 governs.
 *
 * <p>Lower-casing the constant name is enough to produce the whole vocabulary because the Java names
 * are already snake-cased: {@code TOOL_CALL} becomes {@code tool_call}, {@code LIFECYCLE_EVENT}
 * becomes {@code lifecycle_event}. There is no hand-written table to fall out of step, so a constant
 * added to either enum is published correctly without anybody remembering this file exists — and
 * {@code ChatWireNamesTest} asserts that for every constant of both enums.
 *
 * <p>{@link Locale#ROOT} is not decoration. {@code "I".toLowerCase()} is {@code "ı"} in a Turkish
 * locale, which would publish {@code "ınterrupted"} on a server whose default locale nobody chose
 * deliberately, and the client would read it as a status it does not recognise.
 */
public final class ChatWireNames {

    private ChatWireNames() {
    }

    /** {@code USER} → {@code "user"}; the value {@code MessageStart.role} carries. */
    public static String of(ChatMessageRole role) {
        return lower(role.name());
    }

    /** {@code INTERRUPTED} → {@code "interrupted"}; the value {@code message_end.status} carries. */
    public static String of(ChatMessageStatus status) {
        return lower(status.name());
    }

    private static String lower(String constant) {
        return constant.toLowerCase(Locale.ROOT);
    }
}
