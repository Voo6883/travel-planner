package com.travelplanner.domain.ai;

/** Which port method produced an {@link AiCallRecord}. Kept coarse — it is a metrics dimension. */
public enum AiOperation {

    /** {@code LlmPort.complete} — one turn of prose. */
    COMPLETE,

    /**
     * A completion that was part of a schema-bound run — {@code StructuredOutputRunner}, including
     * its repair attempt. Set through {@link LlmOptions#operation()}, because the port has no
     * structured method: structured output is a composition over {@link #COMPLETE}, not a provider
     * capability.
     */
    COMPLETE_STRUCTURED,

    /** {@code LlmPort.completeWithTools} — one turn that may request tools. */
    COMPLETE_WITH_TOOLS,

    /** {@code LlmPort.stream} — a {@code Flux<LlmEvent>} turn. */
    STREAM,

    /** {@code EmbeddingPort.embed} / {@code embedBatch}. */
    EMBED
}
