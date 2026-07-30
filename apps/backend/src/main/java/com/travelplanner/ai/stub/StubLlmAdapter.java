package com.travelplanner.ai.stub;

import com.travelplanner.ai.client.LlmProvider;
import com.travelplanner.domain.ai.LlmCompletion;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.MessageRole;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.ai.StopReason;
import com.travelplanner.domain.ai.ToolSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import reactor.core.publisher.Flux;

/**
 * The default provider: deterministic, in-process, and requiring no credentials
 * (PLAN §4.0.7 stub-first; task 14 "do not require live keys in CI").
 *
 * <p>It is the default rather than a test fixture, which is what makes the no-keys rule structural.
 * A fresh checkout, the unit suite, the Testcontainers suite, and CI all run on this adapter, so
 * nothing can quietly start depending on a live provider — the day someone writes a service that
 * needs a real model, CI still passes and the gap is found in review, not in a red pipeline that
 * gets an API key added to unblock it.
 *
 * <p><strong>Deterministic, not random.</strong> The same prompt always produces the same reply, so a
 * golden-file test can assert on it (AI-AGENT-WORKFLOW A3) and a local run is reproducible. The
 * reply echoes a truncated form of the last user message so a developer can see the wiring worked.
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p>It never invents travel facts. {@code ADR 010} §3 forbids a stub <em>knowledge</em> adapter
 * precisely because fabricated guides carry fabricated provenance; this adapter is the LLM port, not
 * the knowledge port, and it stays useless as a source of facts on purpose — the text it returns is
 * visibly a stub, never a plausible answer about a destination.
 *
 * <p>It never requests a tool. Tool selection is model behaviour that a stub cannot fake usefully,
 * and a stub that invented tool calls would let an orchestrator bug pass its tests.
 */
public final class StubLlmAdapter implements LlmProvider {

    /** The marker every stub reply carries, so stub output is never mistaken for a model answer. */
    public static final String STUB_MARKER = "[stub]";

    private static final int ECHO_LIMIT = 120;

    @Override
    public String providerName() {
        return "stub";
    }

    /**
     * Not a real model name, and priced at nothing by {@code AiCostEstimator} because no rate is
     * registered for it. That is the honest answer: stub traffic costs nothing, and a row claiming
     * otherwise would put fictional money into a cost dashboard.
     */
    @Override
    public String modelName() {
        return "stub";
    }

    @Override
    public String complete(Prompt prompt, LlmOptions options) {
        return reply(prompt);
    }

    @Override
    public LlmCompletion completeWithTools(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
        return new LlmCompletion(reply(prompt), List.of(), usageFor(prompt), StopReason.END_TURN);
    }

    @Override
    public Flux<LlmEvent> stream(Prompt prompt, LlmOptions options) {
        return stream(prompt, List.of(), options);
    }

    /**
     * Emits the same event shape a real provider does — several {@code TextDelta}s, then
     * {@code Usage}, then {@code Done} — so a consumer written against the stub does not discover on
     * the first live call that it never handled a multi-frame stream.
     */
    @Override
    public Flux<LlmEvent> stream(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
        return Flux.defer(() -> {
            List<LlmEvent> events = new ArrayList<>();
            for (String chunk : reply(prompt).split("(?<= )")) {
                events.add(new LlmEvent.TextDelta(chunk));
            }
            events.add(usageFor(prompt));
            events.add(new LlmEvent.Done(StopReason.END_TURN));
            return Flux.fromIterable(events);
        });
    }

    private String reply(Prompt prompt) {
        String lastUserText = prompt.messages().stream()
                .filter(message -> message.role() == MessageRole.USER)
                .map(PromptMessage::text)
                .reduce((first, second) -> second)
                .orElse("");
        String echo = lastUserText.length() > ECHO_LIMIT
                ? lastUserText.substring(0, ECHO_LIMIT)
                : lastUserText;
        return STUB_MARKER + " no model configured. Received: "
                + echo.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * A crude but stable token estimate — roughly four characters per token. Real enough that
     * {@code ai_call_log} has non-zero rows to exercise in tests, and obviously not a billing figure.
     */
    private LlmEvent.Usage usageFor(Prompt prompt) {
        int promptChars = prompt.messages().stream().mapToInt(message -> message.text().length()).sum();
        return new LlmEvent.Usage(promptChars / 4, reply(prompt).length() / 4, 0);
    }
}
