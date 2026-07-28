package com.travelplanner.domain.ai;

import java.util.List;
import java.util.Objects;

/**
 * A rendered conversation, ready to send.
 *
 * <p>Carries the template identity alongside the messages so that {@code ai_call_log} can record
 * <em>which prompt version</em> produced a result without recording the prompt itself (PLAN §5.3,
 * AI-AGENT-WORKFLOW A4). "Version 3 of {@code trip-brief-extract} started failing" is the question
 * operations actually needs to answer, and it is answerable from an id and a hash.
 *
 * @param templateId the {@code PromptTemplateStore} key, or {@code "ad-hoc"} when a caller built the
 *     messages directly
 * @param templateVersion the template revision; {@code 0} for ad-hoc prompts
 */
public record Prompt(String templateId, int templateVersion, List<PromptMessage> messages) {

    /** The template id recorded when a prompt was assembled in code rather than from the store. */
    public static final String AD_HOC = "ad-hoc";

    public Prompt {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(messages, "messages");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("A prompt needs at least one message");
        }
        messages = List.copyOf(messages);
    }

    public static Prompt adHoc(List<PromptMessage> messages) {
        return new Prompt(AD_HOC, 0, messages);
    }

    /** The system instructions, joined, or an empty string. Adapters need them as one block. */
    public String systemText() {
        return messages.stream()
                .filter(message -> message.role() == MessageRole.SYSTEM)
                .map(PromptMessage::text)
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse("");
    }

    /** Everything except the system messages, in order. */
    public List<PromptMessage> conversation() {
        return messages.stream().filter(message -> message.role() != MessageRole.SYSTEM).toList();
    }
}
