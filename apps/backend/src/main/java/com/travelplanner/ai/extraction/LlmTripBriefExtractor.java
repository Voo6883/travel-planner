package com.travelplanner.ai.extraction;

import com.travelplanner.ai.guardrails.Guardrails;
import com.travelplanner.ai.prompt.PromptTemplate;
import com.travelplanner.ai.prompt.PromptTemplateStore;
import com.travelplanner.ai.structured.StructuredOutputRunner;
import com.travelplanner.ai.structured.StructuredRequest;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.model.TripBriefExtraction;
import com.travelplanner.domain.model.TripBriefExtractionRequest;
import com.travelplanner.domain.port.TripBriefExtractionPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link TripBriefExtractionPort} over the provider-neutral structured-completion path (task 19;
 * PLAN §5.2, §6 item 3).
 *
 * <p>It composes what task 14 already built and adds no second AI stack:
 * {@link StructuredOutputRunner} for the schema instruction, the JSON extraction, and the single
 * repair attempt; {@code LlmClientRouter} underneath it for provider routing, the retry budget, the
 * circuit breaker, and the {@code ai_call_log} row that carries tokens, latency, and cost. This
 * class contributes the prompt, the fencing, and the failure policy — nothing else.
 *
 * <h2>The retry budget, stated once</h2>
 *
 * <p>Per {@link #extract} call, in the worst case:
 *
 * <ul>
 *   <li><strong>2</strong> structured-completion attempts — the original plus one repair, capped by
 *       {@code StructuredOutputRunner}, which feeds the parse error back so the second attempt is
 *       informed rather than identical;</li>
 *   <li>each of those bounded by the router's {@code AiRetryPolicy} (default 3 attempts) and only
 *       for failures the provider mapper marked retryable — a timeout is not one of them;</li>
 *   <li><strong>0</strong> retries added here. There is no loop in this method. A malformed
 *       response ends the call.</li>
 * </ul>
 *
 * <p>An unbounded retry against a metered API is a cost incident, so the cap is structural: the
 * only way to raise it is to change a constant in {@code StructuredOutputRunner} or a configured
 * {@code maxAttempts}, both of which are reviewed values rather than a {@code while} loop nobody
 * reads.
 *
 * <h2>Failure is a fallback, never an exception</h2>
 *
 * <p>Every {@link AiProviderException} — timeout, unavailable, rate limited, invalid response —
 * becomes {@link TripBriefExtraction#fallback}. The intake form works without a model, so an
 * outage must degrade to it. Turning a provider problem into a 502 on the brief screen would take
 * away the deterministic path the traveller could still have used.
 */
public final class LlmTripBriefExtractor implements TripBriefExtractionPort {

    private static final Logger log = LoggerFactory.getLogger(LlmTripBriefExtractor.class);

    /**
     * A brief is a dozen short fields. A larger ceiling buys nothing and lets a model that has
     * started narrating run up the bill before the parse fails anyway.
     */
    private static final int MAX_OUTPUT_TOKENS = 1024;

    private final StructuredOutputRunner runner;
    private final PromptTemplateStore prompts;
    private final Clock clock;

    public LlmTripBriefExtractor(StructuredOutputRunner runner, PromptTemplateStore prompts,
            Clock clock) {
        this.runner = runner;
        this.prompts = prompts;
        this.clock = clock;
    }

    @Override
    public TripBriefExtraction extract(TripBriefExtractionRequest request) {
        Prompt prompt = promptFor(request);
        try {
            TripBriefPayload payload = runner.run(new StructuredRequest<>(prompt,
                    TripBriefPayload.class, TripBriefExtractionPrompt.SCHEMA, options()));
            return TripBriefExtraction.from(payload, request.known(),
                    TripBriefExtractionPrompt.versionLabel());
        } catch (AiProviderException failure) {
            // Deliberately broad within the AI failure type: from the traveller's side a timeout, an
            // open breaker, and unparseable output are the same event — the assistant did not
            // answer — and all three have the same correct response, which is the form.
            log.warn("Trip brief extraction fell back to the form: prompt={} code={}",
                    TripBriefExtractionPrompt.versionLabel(), failure.code());
            return TripBriefExtraction.fallback(request.known(), failure.code(),
                    TripBriefExtractionPrompt.versionLabel());
        }
    }

    /**
     * System instructions first, the traveller's fenced words second, and nothing in between.
     *
     * <p>{@link Guardrails#requireSeparated} runs on the assembled prompt rather than being trusted
     * to the reader. It is cheap, it fails loudly, and it is the assertion that keeps this method
     * honest if somebody later "just appends the message" to the system block.
     */
    private Prompt promptFor(TripBriefExtractionRequest request) {
        PromptTemplate template = prompts.version(TripBriefExtractionPrompt.ID,
                TripBriefExtractionPrompt.VERSION);
        String instructions = TripBriefExtractionPrompt.render(template, request.locale(), today());
        Prompt prompt = new Prompt(template.id(), template.version(), List.of(
                PromptMessage.system(instructions),
                PromptMessage.user(Guardrails.asUntrustedData(request.userText()))));
        Guardrails.requireSeparated(prompt);
        return prompt;
    }

    /**
     * A named feature, so {@code travelplanner.ai.routing} can pin extraction to one provider and
     * {@code ai_call_log} can be grouped by it. Temperature is forced to zero by the runner.
     */
    private static LlmOptions options() {
        return LlmOptions.builder()
                .feature(TripBriefExtractionPrompt.FEATURE)
                .maxOutputTokens(MAX_OUTPUT_TOKENS)
                .build();
    }

    /**
     * UTC, because the prompt uses it only to resolve "next April" into a year. A traveller's own
     * timezone would make the same message extract differently depending on where it was sent
     * from, which is a reproducibility problem for one day's worth of accuracy.
     */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
