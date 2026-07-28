package com.travelplanner.ai.observability;

import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Reduces a prompt to a SHA-256 hex digest for {@code ai_call_log}.
 *
 * <p>This is the mechanism behind "tokens and latency, not full prompts" (AI-AGENT-WORKFLOW A4) and
 * "no PII in logs" (PLAN §9). A prompt contains the traveller's dates, budget, companions, and
 * whatever they typed into chat; storing it would put personal data into an operational table that
 * has a long retention and a wide read audience, for the sake of questions a hash answers just as
 * well:
 *
 * <ul>
 *   <li>Is this the same prompt that failed an hour ago? — equal hashes.</li>
 *   <li>Did the retry send something different? — different hashes.</li>
 *   <li>Which prompt version regressed? — {@code prompt_template_id} plus the hash.</li>
 * </ul>
 *
 * <p>The digest is one-way, so a hash cannot be turned back into someone's itinerary. It is not a
 * confidentiality guarantee against a targeted guess — a short, low-entropy prompt could be
 * confirmed by an attacker who already suspects its exact text — which is a further reason the field
 * is a comparison key and never a substitute for the prompt.
 */
public final class PromptHasher {

    /**
     * ASCII unit separator. A control character rather than a printable delimiter so that message
     * text containing the delimiter cannot make a differently-split conversation hash identically.
     */
    private static final char FIELD_SEPARATOR = 0x1F;

    private PromptHasher() {
    }

    public static String hash(Prompt prompt) {
        if (prompt == null) {
            return "";
        }
        StringBuilder canonical = new StringBuilder(prompt.templateId())
                .append('@')
                .append(prompt.templateVersion());
        for (PromptMessage message : prompt.messages()) {
            canonical.append(FIELD_SEPARATOR).append(message.role()).append(':').append(message.text());
        }
        return hash(canonical.toString());
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every JVM", impossible);
        }
    }
}
