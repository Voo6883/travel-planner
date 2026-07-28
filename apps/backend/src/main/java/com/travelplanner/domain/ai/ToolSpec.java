package com.travelplanner.domain.ai;

import java.util.Objects;

/**
 * A tool offered to a model, declared once in project types and translated per provider (PLAN §5.4).
 *
 * @param parametersJsonSchema a JSON Schema object, as text. Text rather than a parsed tree because
 *     {@code domain/} may not depend on Jackson; {@code ai/langchain4j/} parses it.
 *
 *     <p>Its second job matters more than its first: the same schema that tells the model what to
 *     send is the schema the orchestrator validates the model's arguments against before executing
 *     anything ({@code docs/AGENT-HARNESS.md} §4 — a state-mutating tool is never invoked from
 *     free text). One declaration, so the two can never disagree.
 */
public record ToolSpec(String name, String description, String parametersJsonSchema) {

    public ToolSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(parametersJsonSchema, "parametersJsonSchema");
        if (name.isBlank()) {
            throw new IllegalArgumentException("A tool needs a name");
        }
        if (description.isBlank()) {
            // A model chooses tools by their description. An unnamed purpose produces a model that
            // guesses, which is the failure mode tool schemas exist to remove.
            throw new IllegalArgumentException("Tool '" + name + "' needs a description");
        }
    }
}
