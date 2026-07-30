package com.travelplanner.ai.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.domain.ai.AiOperation;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.port.LlmPort;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Binds model output to a typed object (PLAN §5.2 {@code StructuredOutputRunner}, §6 item 3).
 *
 * <p>Composed <em>over</em> {@link LlmPort#complete} rather than implemented inside each adapter.
 * That means the schema instruction, the JSON extraction, the bounded repair attempt, and the typed
 * failure are written once and apply identically to Anthropic, OpenAI, and the stub — so a test that
 * runs against the stub exercises the same code path production does. Three separate
 * implementations would have differed exactly where it costs most: what happens when the model
 * answers with prose around the JSON.
 *
 * <h2>One repair attempt, then a typed failure</h2>
 *
 * <p>Malformed output gets a single retry with the parse error fed back — models correct genuine
 * formatting slips on the first nudge. Beyond that it stops: a model that has produced invalid JSON
 * twice under the same prompt will not produce valid JSON on the third identical attempt, and each
 * further attempt costs tokens and user-visible latency.
 *
 * <p>The failure is {@code ai_response_invalid}, never a partially populated object. PLAN §4.1
 * requires "no confident recommendation" to be a valid typed result rather than something invented
 * to fill the fields, and a half-parsed itinerary is exactly the invented result it forbids.
 */
public final class StructuredOutputRunner {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputRunner.class);

    /** Total attempts: the original plus one repair. */
    private static final int MAX_ATTEMPTS = 2;

    private final LlmPort llm;
    private final ObjectMapper objectMapper;

    public StructuredOutputRunner(LlmPort llm, ObjectMapper objectMapper) {
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    /**
     * @param request bundles prompt, target type, and options — PLAN §4.0.4's remedy for the
     *     ≤3-parameter rule, and it keeps the generic parameter on one type
     * @throws AiProviderException {@code ai_response_invalid} when no attempt parses
     */
    public <T> T run(StructuredRequest<T> request) {
        Prompt prompt = withSchemaInstruction(request);
        String lastError = "";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String raw = llm.complete(prompt, optionsFor(request));
            try {
                return objectMapper.readValue(JsonExtractor.extract(raw), request.type());
            } catch (Exception failure) {
                lastError = failure.getMessage();
                log.warn("Structured output attempt {}/{} failed to parse as {}", attempt,
                        MAX_ATTEMPTS, request.type().getSimpleName());
                prompt = withRepairInstruction(prompt, lastError);
            }
        }
        throw AiProviderException.responseInvalid(
                request.type().getSimpleName() + ": " + lastError);
    }

    /**
     * Appends the schema as a system instruction.
     *
     * <p>A system message rather than an appended user message: providers weight system instructions
     * more strongly, and a format rule buried at the end of the user's own text competes with the
     * user's actual request for attention.
     */
    private <T> Prompt withSchemaInstruction(StructuredRequest<T> request) {
        List<PromptMessage> messages = new ArrayList<>(request.prompt().messages());
        messages.add(PromptMessage.system(
                "Respond with a single JSON object and nothing else — no prose, no markdown fence. "
                        + "It must satisfy this JSON Schema:\n" + request.jsonSchema()));
        return new Prompt(request.prompt().templateId(), request.prompt().templateVersion(), messages);
    }

    private static Prompt withRepairInstruction(Prompt prompt, String parseError) {
        List<PromptMessage> messages = new ArrayList<>(prompt.messages());
        messages.add(PromptMessage.user(
                "That response could not be parsed: " + parseError
                        + ". Reply again with only the JSON object."));
        return new Prompt(prompt.templateId(), prompt.templateVersion(), messages);
    }

    /**
     * Temperature is forced to zero. Structured extraction is not a creative task, and sampling
     * variance here shows up as intermittently malformed JSON — a flaky failure that looks like a
     * provider problem and is not.
     */
    private static <T> LlmOptions optionsFor(StructuredRequest<T> request) {
        return LlmOptions.builder()
                .feature(request.options().feature())
                .model(request.options().model())
                .maxOutputTokens(request.options().maxOutputTokens())
                .timeout(request.options().timeout())
                .temperature(0.0)
                // Each attempt reaches the provider as a plain completion, and each attempt is
                // separately billed. Labelling them keeps the repair round visible in ai_call_log
                // as part of an extraction rather than as unexplained extra prose traffic.
                .operation(AiOperation.COMPLETE_STRUCTURED)
                .build();
    }
}
