package com.travelplanner.ai.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.ai.StopReason;
import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.exception.AiProviderException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * The adapter contract, exercised with fake LangChain4j models — no network and no credentials
 * (task 14 Validation: "adapter contract tests using fakes/mocks").
 *
 * <p>Anthropic and OpenAI reach this same code, because LangChain4j normalises their SSE dialects
 * behind {@code StreamingChatResponseHandler}. The provider-specific parts that remain — cached-token
 * accounting — are tested separately below with each vendor's own {@code TokenUsage} subclass.
 */
class LangChain4jAdapterContractTest {

    private static final Prompt PROMPT = Prompt.adHoc(List.of(
            PromptMessage.system("You are a travel planner."),
            PromptMessage.user("Plan Tokyo")));

    // ---------------------------------------------------------------------------------------
    // Streaming → the ADR 007 union
    // ---------------------------------------------------------------------------------------

    @Test
    void mapsTextDeltasThenUsageThenDone() {
        FakeAdapter adapter = streaming(handler -> {
            handler.onPartialResponse("Tokyo ");
            handler.onPartialResponse("in spring");
            handler.onCompleteResponse(response("Tokyo in spring", new TokenUsage(12, 4)));
        });

        StepVerifier.create(adapter.stream(PROMPT, LlmOptions.defaults()))
                .expectNext(new LlmEvent.TextDelta("Tokyo "))
                .expectNext(new LlmEvent.TextDelta("in spring"))
                .expectNext(new LlmEvent.Usage(12, 4, 0))
                .expectNext(new LlmEvent.Done(StopReason.END_TURN))
                .verifyComplete();
    }

    /**
     * Providers do not announce a tool call; they emit the first argument fragment with a name on it.
     * {@code ToolUseStart} is synthesised on first sight of the id, so a UI can react before the
     * arguments finish arriving.
     */
    @Test
    void synthesisesToolUseStartExactlyOncePerCallId() {
        FakeAdapter adapter = streaming(handler -> {
            handler.onPartialToolCall(partialToolCall("call_1", "create_trip", "{\"name\":"));
            handler.onPartialToolCall(partialToolCall("call_1", "create_trip", "\"Tokyo\"}"));
            handler.onCompleteToolCall(new CompleteToolCall(0, ToolExecutionRequest.builder()
                    .id("call_1").name("create_trip").arguments("{\"name\":\"Tokyo\"}").build()));
            handler.onCompleteResponse(response("", new TokenUsage(5, 2)));
        });

        List<LlmEvent> events = adapter.stream(PROMPT, LlmOptions.defaults()).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).filteredOn(LlmEvent.ToolUseStart.class::isInstance).hasSize(1);
        assertThat(events.subList(0, 4)).containsExactly(
                new LlmEvent.ToolUseStart("call_1", "create_trip"),
                new LlmEvent.ToolInputDelta("call_1", "{\"name\":"),
                new LlmEvent.ToolInputDelta("call_1", "\"Tokyo\"}"),
                new LlmEvent.ToolUseEnd("call_1"));
    }

    /** A provider that batched the whole call into one frame still has to produce a start event. */
    @Test
    void synthesisesAStartForAToolCallThatArrivedWithNoPartials() {
        FakeAdapter adapter = streaming(handler -> {
            handler.onCompleteToolCall(new CompleteToolCall(0, ToolExecutionRequest.builder()
                    .id("call_9").name("create_trip").arguments("{}").build()));
            handler.onCompleteResponse(response("", new TokenUsage(1, 1)));
        });

        List<LlmEvent> events = adapter.stream(PROMPT, LlmOptions.defaults()).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.get(0)).isEqualTo(new LlmEvent.ToolUseStart("call_9", "create_trip"));
        assertThat(events.get(2)).isEqualTo(new LlmEvent.ToolUseEnd("call_9"));
    }

    @Test
    void mapsEachProviderFinishReasonOntoAStopReason() {
        assertThat(stopReasonFor(FinishReason.STOP)).isEqualTo(StopReason.END_TURN);
        assertThat(stopReasonFor(FinishReason.LENGTH)).isEqualTo(StopReason.MAX_TOKENS);
        assertThat(stopReasonFor(FinishReason.TOOL_EXECUTION)).isEqualTo(StopReason.TOOL_USE);
        assertThat(stopReasonFor(FinishReason.CONTENT_FILTER)).isEqualTo(StopReason.CONTENT_FILTERED);
        assertThat(stopReasonFor(FinishReason.OTHER)).isEqualTo(StopReason.OTHER);
    }

    /**
     * ADR 007: a stream that has already returned {@code 200} must deliver its error as a frame, not
     * as an abort — a subscriber cannot otherwise tell a model failure from a dropped connection.
     */
    @Test
    void endsAFailedStreamWithAnErrorFrameAndCompletesRatherThanAborting() {
        FakeAdapter adapter = streaming(handler -> {
            handler.onPartialResponse("Tok");
            handler.onError(new RateLimitException("429"));
        });

        StepVerifier.create(adapter.stream(PROMPT, LlmOptions.defaults()))
                .expectNext(new LlmEvent.TextDelta("Tok"))
                .assertNext(event -> assertThat(event)
                        .isInstanceOf(LlmEvent.StreamError.class)
                        .extracting("code").isEqualTo("ai_rate_limited"))
                .verifyComplete();
    }

    @Test
    void emitsUsageBeforeDoneSoAConsumerStoppingAtDoneStillSeesTheTokens() {
        FakeAdapter adapter = streaming(handler ->
                handler.onCompleteResponse(response("hi", new TokenUsage(3, 1))));

        List<LlmEvent> events = adapter.stream(PROMPT, LlmOptions.defaults()).collectList().block();

        assertThat(events).isNotNull().hasSize(2);
        assertThat(events.get(0)).isInstanceOf(LlmEvent.Usage.class);
        assertThat(events.get(1)).isInstanceOf(LlmEvent.Done.class);
    }

    // ---------------------------------------------------------------------------------------
    // Provider-specific cached-token accounting — the one place the two genuinely differ
    // ---------------------------------------------------------------------------------------

    @Test
    void readsAnthropicCacheReadTokensAsCachedTokens() {
        AnthropicTokenUsage usage = AnthropicTokenUsage.builder()
                .inputTokenCount(100).outputTokenCount(20).cacheReadInputTokens(80).build();

        assertThat(TokenUsageMapper.toUsage(usage)).isEqualTo(new LlmEvent.Usage(100, 20, 80));
    }

    @Test
    void readsOpenAiCachedInputTokensAsCachedTokens() {
        OpenAiTokenUsage usage = OpenAiTokenUsage.builder()
                .inputTokenCount(100).outputTokenCount(20)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(64).build())
                .build();

        assertThat(TokenUsageMapper.toUsage(usage)).isEqualTo(new LlmEvent.Usage(100, 20, 64));
    }

    @Test
    void reportsZeroTokensRatherThanNullWhenAProviderSaysNothing() {
        assertThat(TokenUsageMapper.toUsage(null)).isEqualTo(LlmEvent.Usage.none());
    }

    // ---------------------------------------------------------------------------------------
    // Error normalisation
    // ---------------------------------------------------------------------------------------

    @Test
    void classifiesAServerFaultAsRetryable() {
        AiProviderException mapped = ProviderErrorMapper.map("openai", new InternalServerException("500"));

        assertThat(mapped.code()).isEqualTo("ai_unavailable");
        assertThat(mapped.retryable()).isTrue();
    }

    /** A rotated key must not hide behind a retry loop and an intermittent-looking error rate. */
    @Test
    void classifiesAnAuthenticationFailureAsPermanent() {
        AiProviderException mapped =
                ProviderErrorMapper.map("anthropic", new AuthenticationException("401"));

        assertThat(mapped.code()).isEqualTo("ai_unavailable");
        assertThat(mapped.retryable()).isFalse();
        assertThat(mapped.getMessage()).contains("misconfigured");
    }

    @Test
    void classifiesATimeoutAsNonRetryableBecauseTheBudgetIsAlreadySpent() {
        AiProviderException mapped =
                ProviderErrorMapper.map("openai", new java.util.concurrent.TimeoutException("slow"));

        assertThat(mapped.code()).isEqualTo("ai_timeout");
        assertThat(mapped.retryable()).isFalse();
    }

    @Test
    void unwrapsAsyncWrappersToFindTheVendorClassification() {
        AiProviderException mapped = ProviderErrorMapper.map("openai",
                new java.util.concurrent.CompletionException(new RateLimitException("429")));

        assertThat(mapped.code()).isEqualTo("ai_rate_limited");
    }

    @Test
    void leavesAnAlreadyNormalisedFailureAlone() {
        AiProviderException original = AiProviderException.responseInvalid("schema");

        assertThat(ProviderErrorMapper.map("openai", original)).isSameAs(original);
    }

    // ---------------------------------------------------------------------------------------
    // Request mapping
    // ---------------------------------------------------------------------------------------

    @Test
    void mergesSystemMessagesIntoOneLeadingBlock() {
        Prompt prompt = Prompt.adHoc(List.of(
                PromptMessage.system("Be concise."),
                PromptMessage.user("Plan Tokyo"),
                PromptMessage.system("Cite sources.")));

        ChatRequest request = LangChain4jRequestMapper.toRequest(prompt, List.of(), null);

        assertThat(request.messages().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) request.messages().get(0)).text())
                .contains("Be concise.").contains("Cite sources.");
        assertThat(request.messages()).hasSize(2);
    }

    @Test
    void mapsEachRoleOntoItsLangChain4jMessageType() {
        Prompt prompt = Prompt.adHoc(List.of(
                PromptMessage.user("Plan Tokyo"),
                PromptMessage.assistant("Sure."),
                PromptMessage.toolResult("call_1", "{\"ok\":true}")));

        List<ChatMessage> messages = LangChain4jRequestMapper.toRequest(prompt, List.of(), null)
                .messages();

        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(((ToolExecutionResultMessage) messages.get(2)).id()).isEqualTo("call_1");
    }

    /** PLAN §5.4 — neutral options in, vendor params out, with no caller aware of the difference. */
    @Test
    void appliesOnlyTheOptionsACallerActuallySet() {
        ChatRequest request = LangChain4jRequestMapper.toRequest(PROMPT, List.of(),
                LlmOptions.builder().temperature(0.2).maxOutputTokens(512).build());

        assertThat(request.temperature()).isEqualTo(0.2);
        assertThat(request.maxOutputTokens()).isEqualTo(512);
        assertThat(request.topP()).isNull();
    }

    @Test
    void translatesToolSpecsIncludingTheirJsonSchema() {
        ToolSpec spec = new ToolSpec("create_trip", "Create a trip",
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},"
                        + "\"required\":[\"name\"]}");

        ChatRequest request = LangChain4jRequestMapper.toRequest(PROMPT, List.of(spec), null);

        assertThat(request.toolSpecifications()).singleElement().satisfies(translated -> {
            assertThat(translated.name()).isEqualTo("create_trip");
            assertThat(translated.parameters().properties()).containsKey("name");
            assertThat(translated.parameters().required()).containsExactly("name");
        });
    }

    /** A tool whose arguments cannot be validated must never be offered to a model. */
    @Test
    void refusesAToolWithAMalformedSchema() {
        ToolSpec spec = new ToolSpec("broken", "Broken", "not json");

        assertThatThrownBy(() -> LangChain4jRequestMapper.toRequest(PROMPT, List.of(spec), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("broken");
    }

    // ---------------------------------------------------------------------------------------

    private static StopReason stopReasonFor(FinishReason finishReason) {
        FakeAdapter adapter = streaming(handler -> handler.onCompleteResponse(ChatResponse.builder()
                .aiMessage(AiMessage.from("x"))
                .tokenUsage(new TokenUsage(1, 1))
                .finishReason(finishReason)
                .build()));
        LlmEvent.Done done = (LlmEvent.Done) adapter.stream(PROMPT, LlmOptions.defaults())
                .filter(LlmEvent.Done.class::isInstance).blockFirst();
        return done == null ? null : done.stopReason();
    }

    private static ChatResponse response(String text, TokenUsage usage) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .tokenUsage(usage)
                .finishReason(FinishReason.STOP)
                .build();
    }

    private static PartialToolCall partialToolCall(String id, String name, String chunk) {
        return PartialToolCall.builder().index(0).id(id).name(name).partialArguments(chunk).build();
    }

    private static FakeAdapter streaming(Consumer<StreamingChatResponseHandler> script) {
        return new FakeAdapter(script);
    }

    /** The real adapter base, driven by a scripted model instead of a provider. */
    private static final class FakeAdapter extends LangChain4jLlmAdapter {

        private final Consumer<StreamingChatResponseHandler> script;

        private FakeAdapter(Consumer<StreamingChatResponseHandler> script) {
            super("fake");
            this.script = script;
        }

        // Anonymous classes rather than lambdas: every method on these interfaces is `default`, so
        // neither is functional. `doChat` is the one the public `chat` overloads delegate to.
        @Override
        protected ChatModel chatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return response("blocking", new TokenUsage(1, 1));
                }
            };
        }

        @Override
        protected StreamingChatModel streamingChatModel() {
            return new StreamingChatModel() {
                @Override
                public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                    script.accept(handler);
                }
            };
        }
    }
}
