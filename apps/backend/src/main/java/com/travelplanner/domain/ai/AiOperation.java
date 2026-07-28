package com.travelplanner.domain.ai;

/** Which port method produced an {@link AiCallRecord}. Kept coarse — it is a metrics dimension. */
public enum AiOperation {

    /** {@code LlmPort.complete} — one turn of prose. */
    COMPLETE,

    /** {@code LlmPort.completeStructured} — schema-bound output. */
    COMPLETE_STRUCTURED,

    /** {@code LlmPort.completeWithTools} — one turn that may request tools. */
    COMPLETE_WITH_TOOLS,

    /** {@code LlmPort.stream} — a {@code Flux<LlmEvent>} turn. */
    STREAM,

    /** {@code EmbeddingPort.embed} / {@code embedBatch}. */
    EMBED
}
