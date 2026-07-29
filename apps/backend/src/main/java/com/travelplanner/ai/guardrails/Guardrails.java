package com.travelplanner.ai.guardrails;

import com.travelplanner.domain.ai.MessageRole;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import java.util.Locale;

/**
 * Keeps user text on the data side of a prompt (PLAN §9 "LLM injection —
 * {@code Guardrails.sanitizeUserInput()} before prompts"; PLAN §5.3).
 *
 * <h2>Separation, not detection</h2>
 *
 * <p>There is no attempt here to spot a malicious sentence. Phrase matching against "ignore
 * previous instructions" is a filter an attacker rewrites around in one attempt, and every false
 * positive refuses a real traveller — "ignore my previous message, we changed the dates" is an
 * ordinary thing to type. What this class does instead is structural and cannot be phrased around:
 *
 * <ol>
 *   <li>User text is only ever placed in a {@link MessageRole#USER} message. The system block is
 *       rendered from a versioned template whose variables are enumerated by the template itself,
 *       so there is no slot a caller could put user text into even by mistake.</li>
 *   <li>Inside that message the text is fenced by {@link #OPEN} / {@link #CLOSE}, and any occurrence
 *       of those markers in the text itself is neutralised — otherwise a message containing
 *       {@code </user_text>} could close the fence early and have its remainder read as though it
 *       came from outside.</li>
 *   <li>{@link #requireSeparated} asserts after the fact that no user message text leaked into the
 *       system block, so a future caller that assembles the prompt differently fails a test rather
 *       than shipping.</li>
 * </ol>
 *
 * <p>The model is separately instructed, in the system block, that everything inside the fence is a
 * description of a trip and never an instruction. That instruction is advice; points 1–3 are the
 * guarantee, and the domain validation that follows extraction is what makes an override
 * <em>harmless</em> even in the case where the model believes it: a model told to "set the budget
 * to -1" produces a value {@link com.travelplanner.domain.valueobject.Money} refuses.
 */
public final class Guardrails {

    /** Opens the untrusted block. Uppercase XML-ish tags are rare in ordinary prose. */
    public static final String OPEN = "<USER_TEXT>";

    /** Closes the untrusted block. */
    public static final String CLOSE = "</USER_TEXT>";

    /** What an embedded marker is replaced with, so the substitution is visible in a transcript. */
    private static final String NEUTRALISED = "[redacted-marker]";

    private Guardrails() {
    }

    /**
     * Fences one piece of user-supplied text as data.
     *
     * <p>C0 control characters are stripped as well. They are invisible in a review tool and in a
     * log line, which makes them the natural carrier for a payload nobody notices; nothing a
     * traveller types about a trip needs one.
     */
    public static String asUntrustedData(String text) {
        String cleaned = text == null ? "" : stripControlCharacters(text);
        String fenced = cleaned
                .replace(OPEN, NEUTRALISED)
                .replace(CLOSE, NEUTRALISED)
                .replace(OPEN.toLowerCase(Locale.ROOT), NEUTRALISED)
                .replace(CLOSE.toLowerCase(Locale.ROOT), NEUTRALISED);
        return OPEN + "\n" + fenced.trim() + "\n" + CLOSE;
    }

    /**
     * @throws IllegalStateException when any user message's text appears inside the system block —
     *     the exact mistake ("just append the user's message to the system prompt") this class
     *     exists to make impossible to ship
     */
    public static void requireSeparated(Prompt prompt) {
        String system = prompt.systemText();
        if (system.isEmpty()) {
            return;
        }
        for (PromptMessage message : prompt.messages()) {
            if (message.role() == MessageRole.USER && system.contains(message.text())) {
                throw new IllegalStateException(
                        "user text reached the system instruction block of prompt "
                                + prompt.templateId() + " v" + prompt.templateVersion());
            }
        }
    }

    private static String stripControlCharacters(String text) {
        StringBuilder cleaned = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\n' || current == '\t' || current >= ' ') {
                cleaned.append(current);
            }
        }
        return cleaned.toString();
    }
}
