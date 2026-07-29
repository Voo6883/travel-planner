package com.travelplanner.ai.extraction;

import com.travelplanner.domain.ai.LlmCompletion;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.port.LlmPort;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * A provider that says what the test tells it to, and remembers what it was asked.
 *
 * <p>It stands in for the network, not for the platform: every test using it still runs the real
 * {@code StructuredOutputRunner}, the real prompt template, and the real domain validation, so what
 * is being asserted is the path production takes. The deterministic stub adapter cannot be used
 * here for the successful cases — by design it returns a visibly-stub sentence rather than JSON —
 * but the golden-file suite exercises exactly that behaviour through {@code malformed.json}.
 *
 * <p>The last scripted reply repeats rather than running out. A model that answered with prose once
 * usually answers with prose again, and a fake that quietly switched to an empty string on the
 * repair attempt would test a failure mode no provider has.
 */
final class ScriptedLlm implements LlmPort {

    private final Deque<String> replies = new ArrayDeque<>();
    private final List<Prompt> prompts = new ArrayList<>();
    private AiProviderException failure;

    ScriptedLlm reply(String text) {
        replies.add(text);
        return this;
    }

    ScriptedLlm failsWith(AiProviderException value) {
        this.failure = value;
        return this;
    }

    List<Prompt> prompts() {
        return prompts;
    }

    @Override
    public String providerName() {
        return "scripted";
    }

    @Override
    public String complete(Prompt prompt, LlmOptions options) {
        prompts.add(prompt);
        if (failure != null) {
            throw failure;
        }
        if (replies.isEmpty()) {
            return "";
        }
        return replies.size() == 1 ? replies.peekFirst() : replies.removeFirst();
    }

    @Override
    public <T> T completeStructured(Prompt prompt, Class<T> type, LlmOptions options) {
        throw new UnsupportedOperationException("composed by StructuredOutputRunner");
    }

    @Override
    public LlmCompletion completeWithTools(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
        return LlmCompletion.ofText(complete(prompt, options), LlmEvent.Usage.none());
    }

    @Override
    public Flux<LlmEvent> stream(Prompt prompt, LlmOptions options) {
        return Flux.empty();
    }

    @Override
    public Flux<LlmEvent> stream(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
        return Flux.empty();
    }
}
