package com.travelplanner.ai.langchain4j;

import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.MessageRole;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.ai.ToolSpec;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.ArrayList;
import java.util.List;

/**
 * Project types to LangChain4j request types. Confined to this package by the AGENTS.md import rule.
 *
 * <p>This is where PLAN §5.4's "keep {@code LlmOptions} provider-neutral; map to vendor params inside
 * each adapter" is actually paid for. {@code maxOutputTokens} lands on {@code maxOutputTokens} here;
 * LangChain4j then renders it as Anthropic's {@code max_tokens} or OpenAI's
 * {@code max_completion_tokens}. No caller upstream has to know which.
 *
 * <p>Null options are left unset rather than defaulted, so a per-call override is genuinely optional:
 * an unset temperature means "use what the model bean was built with", not "use 0.0" — a difference
 * that turns a creative chat reply into a deterministic one.
 */
final class LangChain4jRequestMapper {

    private LangChain4jRequestMapper() {
    }

    static ChatRequest toRequest(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
        ChatRequest.Builder builder = ChatRequest.builder().messages(toMessages(prompt));
        applyOptions(builder, options);
        if (tools != null && !tools.isEmpty()) {
            builder.toolSpecifications(tools.stream().map(LangChain4jRequestMapper::toSpecification).toList());
        }
        return builder.build();
    }

    private static void applyOptions(ChatRequest.Builder builder, LlmOptions options) {
        if (options == null) {
            return;
        }
        if (options.model() != null) {
            builder.modelName(options.model());
        }
        if (options.temperature() != null) {
            builder.temperature(options.temperature());
        }
        if (options.topP() != null) {
            builder.topP(options.topP());
        }
        if (options.maxOutputTokens() != null) {
            builder.maxOutputTokens(options.maxOutputTokens());
        }
        if (!options.stopSequences().isEmpty()) {
            builder.stopSequences(options.stopSequences());
        }
    }

    private static List<ChatMessage> toMessages(Prompt prompt) {
        List<ChatMessage> messages = new ArrayList<>();
        String system = prompt.systemText();
        if (!system.isBlank()) {
            // One block, first. Anthropic carries it as a top-level field and only accepts it at the
            // start; OpenAI accepts it anywhere but behaves better with it leading. Merging in
            // Prompt.systemText() means both providers get the same instructions.
            messages.add(SystemMessage.from(system));
        }
        for (PromptMessage message : prompt.conversation()) {
            messages.add(toMessage(message));
        }
        return messages;
    }

    private static ChatMessage toMessage(PromptMessage message) {
        if (message.role() == MessageRole.ASSISTANT) {
            return AiMessage.from(message.text());
        }
        if (message.role() == MessageRole.TOOL) {
            // The tool name is not carried: providers correlate on the id, and a name that disagreed
            // with the original request would be rejected. Task 22 owns the tool loop that fills it.
            return ToolExecutionResultMessage.from(message.toolCallId(), null, message.text());
        }
        return UserMessage.from(message.text());
    }

    /**
     * Our JSON Schema text becomes a LangChain4j {@code JsonObjectSchema}.
     *
     * <p>Parsed via {@link ToolSpecification#fromJson(String)} rather than a hand-rolled walk of the
     * schema tree: the library already owns that translation for both providers, and a second
     * implementation would drift on exactly the cases that matter (nested objects, enums, required).
     */
    private static ToolSpecification toSpecification(ToolSpec spec) {
        JsonObjectSchema parameters = parseParameters(spec);
        return ToolSpecification.builder()
                .name(spec.name())
                .description(spec.description())
                .parameters(parameters)
                .build();
    }

    private static JsonObjectSchema parseParameters(ToolSpec spec) {
        String envelope = "{\"name\":\"" + spec.name() + "\",\"description\":\"tool\",\"parameters\":"
                + spec.parametersJsonSchema() + "}";
        try {
            return ToolSpecification.fromJson(envelope).parameters();
        } catch (RuntimeException failure) {
            // A malformed tool schema is a programming error, not a runtime condition: it would let a
            // model be offered a tool whose arguments cannot be validated on the way back.
            throw new IllegalArgumentException(
                    "Tool '" + spec.name() + "' has an invalid JSON Schema", failure);
        }
    }
}
