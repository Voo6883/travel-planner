package com.travelplanner.ai.prompt;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A versioned, named prompt with {@code {{placeholder}}} slots (PLAN §5.3 {@code PromptTemplateStore}).
 *
 * <p>Versioned because a prompt is behaviour. When output quality changes, the first question is
 * "what changed in the prompt", and it can only be answered if {@code ai_call_log} recorded an id and
 * a version alongside the result. It is also what PLAN §15.3's prompt-change CI gate keys on.
 *
 * <h2>Rendering is strict in both directions</h2>
 *
 * <p>A missing variable throws, and so does an unused one. Both are refused because the alternative
 * failure is invisible: a prompt that renders {@code Plan a trip to {{destination}}} literally still
 * produces a fluent, confident, completely wrong answer. There is no exception at generation time
 * and nothing in the output that looks broken — the model simply improvises. Failing at render is the
 * only place this is cheap to catch.
 *
 * @param variant the provider this text is tuned for, or {@code null} for provider-neutral text
 *     (PLAN §5.3: "versioned, per-provider variants")
 */
public record PromptTemplate(String id, int version, String text, String variant) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

    public PromptTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        if (version < 1) {
            throw new IllegalArgumentException("Prompt template versions start at 1");
        }
    }

    public static PromptTemplate of(String id, int version, String text) {
        return new PromptTemplate(id, version, text, null);
    }

    /** The placeholder names this template expects, in first-appearance order. */
    public List<String> variables() {
        return PLACEHOLDER.matcher(text).results()
                .map(result -> result.group(1))
                .distinct()
                .toList();
    }

    /** @throws IllegalArgumentException when a variable is missing, or a supplied one is unused */
    public String render(Map<String, String> values) {
        List<String> expected = variables();
        for (String name : expected) {
            if (!values.containsKey(name)) {
                throw new IllegalArgumentException(
                        "Prompt '" + id + "' v" + version + " needs variable '" + name + "'");
            }
        }
        for (String supplied : values.keySet()) {
            if (!expected.contains(supplied)) {
                // A stale variable usually means the template was edited and a caller was not. Left
                // unchecked, the caller keeps computing an expensive value nobody reads.
                throw new IllegalArgumentException(
                        "Prompt '" + id + "' v" + version + " has no variable '" + supplied + "'");
            }
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(values.get(matcher.group(1))));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
