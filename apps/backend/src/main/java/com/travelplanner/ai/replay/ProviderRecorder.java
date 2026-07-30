package com.travelplanner.ai.replay;

import com.travelplanner.ai.client.LlmProvider;
import com.travelplanner.ai.observability.PromptHasher;
import com.travelplanner.ai.replay.RecordedExchange.RecordedCompletion;
import com.travelplanner.ai.replay.RecordedExchange.RecordedEvent;
import com.travelplanner.ai.replay.RecordedExchange.RecordedToolCall;
import com.travelplanner.ai.replay.RecordedExchange.RecordedUsage;
import com.travelplanner.domain.ai.LlmCompletion;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.ToolSpec;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * Wraps a real provider and writes what it returned as a replayable fixture (review §6.I).
 *
 * <p>A decorator rather than a mode inside each adapter: recording is identical for Anthropic and
 * OpenAI, and the version living in the vendor adapters would be the one that diverged on the case that
 * matters — a tool-call delta sequence, or a usage frame arriving after {@code Done}.
 *
 * <h2>What is recorded, and what is refused</h2>
 *
 * <p><strong>The prompt is never stored — only {@code PromptHasher}'s digest.</strong> That is not
 * merely privacy hygiene. A fixture containing a traveller's message is a file that must not be
 * committed, and a fixture that cannot be committed is not a fixture: it exists on one machine, the
 * suite passes there and fails everywhere else. Hashing is what makes these artefacts shareable, and
 * {@code ProviderRecorderTest} asserts that no prompt text survives.
 *
 * <p>The <em>response</em> is stored verbatim, and that is a deliberate asymmetry worth stating. Model
 * output can echo the prompt back, so a recording made against a real traveller's message can contain
 * that message inside the completion text. This is therefore a <strong>development tool</strong>: record
 * against prompts you wrote for the purpose, review the fixture before committing it, and never point
 * it at production traffic. {@code AiConfigValidator} refuses the recording flag under {@code prod},
 * which stops the accident but cannot stop a deliberate misuse in staging.
 *
 * <h2>Streams are recorded as they pass</h2>
 *
 * <p>{@code doOnNext} accumulates, and the fixture is written on completion. Recording at the end from
 * a buffered copy is the same thing with an extra failure mode: a stream that is cancelled halfway has
 * still produced real events, and those are among the most useful recordings there are — a partial
 * turn is the case ADR 007's {@code INTERRUPTED} status exists for.
 *
 * <p>A failed stream is not written. A recording of a provider outage replays as an outage, which no
 * test wants by accident; a test that wants one constructs it by hand, where the intent is visible.
 */
public final class ProviderRecorder implements LlmProvider {

    private final LlmProvider delegate;
    private final RecordedExchangeStore store;

    public ProviderRecorder(LlmProvider delegate, RecordedExchangeStore store) {
        this.delegate = delegate;
        this.store = store;
    }

    @Override
    public String providerName() {
        return delegate.providerName();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public String complete(Prompt prompt, LlmOptions options) {
        return completeWithTools(prompt, List.of(), options).text();
    }

    @Override
    public LlmCompletion completeWithTools(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
        LlmCompletion completion = delegate.completeWithTools(prompt, tools, options);
        store.save(new RecordedExchange(
                PromptHasher.hash(prompt),
                delegate.providerName(),
                effectiveModel(options),
                toFixture(completion),
                List.of()));
        return completion;
    }

    @Override
    public Flux<LlmEvent> stream(Prompt prompt, LlmOptions options) {
        return stream(prompt, List.of(), options);
    }

    @Override
    public Flux<LlmEvent> stream(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
        List<RecordedEvent> captured = new ArrayList<>();
        return delegate.stream(prompt, tools, options)
                .doOnNext(event -> captured.add(RecordedEventMapper.toFixture(event)))
                // Both endings write, because a cancelled turn is a real recording: a partial stream is
                // the case ADR 007's INTERRUPTED status exists for, and it is hard to fabricate.
                .doOnComplete(() -> persistStream(prompt, options, captured))
                .doOnCancel(() -> persistStream(prompt, options, captured));
    }

    /**
     * A recording is only written when there is something to replay.
     *
     * <p>An empty capture means the stream errored before emitting, and a fixture with no events would
     * fail {@code RecordedExchange}'s own constructor — so this is the guard that keeps a provider
     * outage from becoming a permanently broken fixture on disk.
     */
    private void persistStream(Prompt prompt, LlmOptions options, List<RecordedEvent> captured) {
        if (captured.isEmpty()) {
            return;
        }
        store.save(new RecordedExchange(
                PromptHasher.hash(prompt),
                delegate.providerName(),
                effectiveModel(options),
                null,
                List.copyOf(captured)));
    }

    /** The caller's override when there is one, otherwise the adapter's configured model. */
    private String effectiveModel(LlmOptions options) {
        String override = options == null ? null : options.model();
        return override == null || override.isBlank() ? delegate.modelName() : override;
    }

    private static RecordedCompletion toFixture(LlmCompletion completion) {
        return new RecordedCompletion(
                completion.text(),
                completion.toolCalls().stream()
                        .map(call -> new RecordedToolCall(call.toolCallId(), call.name(), call.argumentsJson()))
                        .toList(),
                new RecordedUsage(completion.usage().inputTokens(), completion.usage().outputTokens(),
                        completion.usage().cachedTokens()),
                completion.stopReason());
    }
}
