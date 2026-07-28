package com.travelplanner.domain.port;

import com.travelplanner.domain.ai.EmbeddingModelRef;
import java.util.List;

/**
 * Text to vector (PLAN §5.1's {@code EmbeddingClient}; ADR 010 §5 pins the model).
 *
 * <h2>{@link #modelRef()} is the whole point</h2>
 *
 * <p>PLAN §5.4: "chat may switch freely; embeddings may not." So unlike {@link LlmPort}, this port
 * publishes <em>which</em> model produced its vectors. Every caller that writes an embedding row is
 * required to persist that reference into the row's {@code embedding_model} /
 * {@code embedding_dimension} columns (ADR 010 §5), and every caller that searches must check it
 * against the rows it is about to compare.
 *
 * <p>Without that, mixing is undetectable. A cosine similarity between vectors from two models is a
 * well-formed number with no meaning: nothing throws, no query fails, retrieval simply returns the
 * wrong POIs and the agent grounds an answer on them. There is exactly one bean of this type — the
 * configuration cannot express a second — so "which model is in the index" always has one answer.
 *
 * <p>There is also no {@code forFeature(...)} here, deliberately. {@code LlmClientRouter} exists so
 * chat can route per feature; an equivalent for embeddings would be a supported way to get two
 * incompatible vector spaces into one index.
 */
public interface EmbeddingPort {

    /** The pinned model behind every vector this port returns. Never {@code null}. */
    EmbeddingModelRef modelRef();

    /** @return a vector of exactly {@code modelRef().dimension()} floats */
    float[] embed(String text);

    /**
     * Batch form. Order matches the input; the provider is called once rather than per item, which
     * is the difference between one round trip and twenty-five when seeding a destination's POIs.
     */
    List<float[]> embedBatch(List<String> texts);
}
