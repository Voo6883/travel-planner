package com.travelplanner.ai.client;

import com.travelplanner.ai.observability.AiCallContext;
import com.travelplanner.ai.observability.AiCallRecorder;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.port.LlmPort;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Flux;

/**
 * Instruments one streaming turn: breaker, {@code ai_call_log}, and cancellation
 * (task 14 Scope: "cancellation"; ADR 007).
 *
 * <p>A class of its own rather than a method on the router, because a stream's bookkeeping runs on a
 * different timeline from the call that created it. The subscription may end minutes after
 * {@code stream(...)} returned, on another thread, in any of three ways — completion, error, or
 * cancellation — and each has to record something different. That is state, and hanging it off a
 * shared router bean would make it per-router instead of per-call.
 *
 * <h2>The three endings</h2>
 *
 * <ul>
 *   <li><strong>Complete</strong> — the {@code Usage} event seen along the way is what gets recorded.
 *       Capturing it as the stream passes is the only opportunity: it is not available before, and
 *       the events are gone after.</li>
 *   <li><strong>{@code StreamError}</strong> — recorded as a failure with the frame's code. The Flux
 *       still completes normally (ADR 007 forbids a bare abort), so the error is detected by watching
 *       the events, not by an {@code onError} hook that will never fire.</li>
 *   <li><strong>Cancel</strong> — the subscriber went away; the SSE connection dropped or the user
 *       navigated. Recorded as {@code CANCELLED}, not {@code ERROR}, so ordinary user behaviour does
 *       not read as an outage. Reactor propagates the cancellation into the adapter, which is what
 *       actually stops the provider call — an abandoned stream that kept generating would keep
 *       billing.</li>
 * </ul>
 */
final class StreamingCall {

    private final LlmClientRouter router;
    private final AiCallRecorder recorder;

    StreamingCall(LlmClientRouter router, AiCallRecorder recorder) {
        this.router = router;
        this.recorder = recorder;
    }

    Flux<LlmEvent> execute(LlmClientRouter.CallSpec spec, List<ToolSpec> tools) {
        LlmPort provider = router.resolve(spec.options());
        AiCallContext context = router.contextFor(spec);
        AtomicReference<LlmEvent.Usage> usage = new AtomicReference<>(LlmEvent.Usage.none());
        AtomicReference<String> errorCode = new AtomicReference<>();
        AtomicLong startedAt = new AtomicLong();

        return Flux.defer(() -> {
            // The breaker is checked at subscribe time, not at assembly time: a cold Flux may be
            // built long before anyone subscribes, and the breaker's answer then would be stale.
            router.requireClosedBreaker(provider.providerName());
            startedAt.set(System.nanoTime());
            return provider.stream(spec.prompt(), tools, spec.options());
        })
                .doOnNext(event -> observe(event, usage, errorCode))
                .doOnComplete(() -> finish(context, new Outcome(usage.get(), errorCode.get(),
                        LlmClientRouter.elapsedMs(startedAt.get()))))
                .doOnCancel(() -> recorder.recordCancelled(context, usage.get(),
                        LlmClientRouter.elapsedMs(startedAt.get())))
                .doOnError(failure -> {
                    // Reached only when the stream failed before it began emitting — the adapter
                    // errors the Flux there because no 200 has been committed yet.
                    router.breaker().recordFailure(provider.providerName());
                    recorder.recordFailure(context, codeOf(failure),
                            LlmClientRouter.elapsedMs(startedAt.get()));
                });
    }

    /** How a stream ended, bundled to keep {@link #finish} at two parameters (PLAN §4.0.4). */
    private record Outcome(LlmEvent.Usage usage, String errorCode, long latencyMs) {
    }

    private static void observe(LlmEvent event, AtomicReference<LlmEvent.Usage> usage,
            AtomicReference<String> errorCode) {
        if (event instanceof LlmEvent.Usage seen) {
            usage.set(seen);
        } else if (event instanceof LlmEvent.StreamError error) {
            errorCode.set(error.code());
        }
    }

    private void finish(AiCallContext context, Outcome outcome) {
        String provider = context.provider();
        if (outcome.errorCode() == null) {
            router.breaker().recordSuccess(provider);
            recorder.recordSuccess(context, outcome.usage(), outcome.latencyMs());
            return;
        }
        router.breaker().recordFailure(provider);
        recorder.recordFailure(context, outcome.errorCode(), outcome.latencyMs());
    }

    private static String codeOf(Throwable failure) {
        return failure instanceof AiProviderException typed
                ? typed.code()
                : AiProviderException.UNAVAILABLE;
    }
}
