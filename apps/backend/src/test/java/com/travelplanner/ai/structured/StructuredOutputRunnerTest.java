package com.travelplanner.ai.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.domain.ai.LlmCompletion;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.port.LlmPort;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/** Structured output: extraction, one repair attempt, then a typed failure — never a half-object. */
class StructuredOutputRunnerTest {

    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"destination\":{\"type\":\"string\"}}}";

    private final ScriptedLlm llm = new ScriptedLlm();
    private final StructuredOutputRunner runner = new StructuredOutputRunner(llm, new ObjectMapper());

    record Brief(String destination) {
    }

    @Test
    void bindsACleanJsonReplyToTheRequestedType() {
        llm.replies.add("{\"destination\":\"Tokyo\"}");

        assertThat(runner.run(request()).destination()).isEqualTo("Tokyo");
    }

    /**
     * Models wrap JSON in a fence or a sentence often enough that rejecting those responses would
     * burn a repair attempt — and sometimes the whole call — on output that was correct.
     */
    @Test
    void extractsJsonFromAFencedReply() {
        llm.replies.add("Sure, here it is:\n```json\n{\"destination\":\"Bangkok\"}\n```");

        assertThat(runner.run(request()).destination()).isEqualTo("Bangkok");
    }

    @Test
    void handlesBracesInsideStringValues() {
        llm.replies.add("{\"destination\":\"Café {Old Town}\"}");

        assertThat(runner.run(request()).destination()).isEqualTo("Café {Old Town}");
    }

    @Test
    void repairsOnceWhenTheFirstReplyDoesNotParse() {
        llm.replies.add("I think you should visit Tokyo.");
        llm.replies.add("{\"destination\":\"Tokyo\"}");

        assertThat(runner.run(request()).destination()).isEqualTo("Tokyo");
        assertThat(llm.prompts).hasSize(2);
    }

    /** The repair prompt must tell the model what went wrong, or the second attempt is a coin flip. */
    @Test
    void feedsTheParseErrorBackIntoTheRepairAttempt() {
        llm.replies.add("not json at all");
        llm.replies.add("{\"destination\":\"Tokyo\"}");

        runner.run(request());

        assertThat(llm.prompts.get(1).messages())
                .extracting(PromptMessage::text)
                .anySatisfy(text -> assertThat(text).contains("could not be parsed"));
    }

    @Test
    void stopsAfterOneRepairRatherThanBurningTokensOnAnUnchangedPrompt() {
        llm.replies.add("nope");
        llm.replies.add("still nope");
        llm.replies.add("{\"destination\":\"Tokyo\"}");

        assertThatThrownBy(() -> runner.run(request()))
                .isInstanceOf(AiProviderException.class)
                .extracting("code").isEqualTo("ai_response_invalid");
        assertThat(llm.prompts).hasSize(2);
    }

    /** PLAN §4.1: "no confident result" is a valid typed outcome; a half-filled object is not. */
    @Test
    void failsTypedRatherThanReturningAPartiallyPopulatedObject() {
        llm.replies.add("prose");
        llm.replies.add("more prose");

        assertThatThrownBy(() -> runner.run(request()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(failure -> assertThat(((AiProviderException) failure).retryable()).isFalse());
    }

    @Test
    void appendsTheSchemaAsASystemInstructionSoItOutweighsTheUsersOwnText() {
        llm.replies.add("{\"destination\":\"Tokyo\"}");

        runner.run(request());

        assertThat(llm.prompts.get(0).systemText()).contains(SCHEMA).contains("single JSON object");
    }

    /** Sampling variance in an extraction task shows up as intermittently malformed JSON. */
    @Test
    void forcesTemperatureToZero() {
        llm.replies.add("{\"destination\":\"Tokyo\"}");

        runner.run(request());

        assertThat(llm.options.get(0).temperature()).isZero();
    }

    private StructuredRequest<Brief> request() {
        return StructuredRequest.of(
                Prompt.adHoc(List.of(PromptMessage.user("Where should I go?"))), Brief.class, SCHEMA);
    }

    /** Replays a script of replies and remembers what it was asked. */
    private static final class ScriptedLlm implements LlmPort {

        private final Deque<String> replies = new ArrayDeque<>();
        private final List<Prompt> prompts = new java.util.ArrayList<>();
        private final List<LlmOptions> options = new java.util.ArrayList<>();

        @Override
        public String providerName() {
            return "scripted";
        }

        @Override
        public String modelName() {
            return "scripted-model";
        }

        @Override
        public String complete(Prompt prompt, LlmOptions callOptions) {
            prompts.add(prompt);
            options.add(callOptions);
            return replies.isEmpty() ? "" : replies.removeFirst();
        }

        @Override
        public LlmCompletion completeWithTools(Prompt prompt, List<ToolSpec> tools,
                LlmOptions callOptions) {
            return LlmCompletion.ofText(complete(prompt, callOptions), LlmEvent.Usage.none());
        }

        @Override
        public Flux<LlmEvent> stream(Prompt prompt, LlmOptions callOptions) {
            return Flux.empty();
        }

        @Override
        public Flux<LlmEvent> stream(Prompt prompt, List<ToolSpec> tools, LlmOptions callOptions) {
            return Flux.empty();
        }
    }
}
