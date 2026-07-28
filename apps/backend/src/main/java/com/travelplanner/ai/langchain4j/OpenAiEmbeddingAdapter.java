package com.travelplanner.ai.langchain4j;

import com.travelplanner.config.AiProperties;
import com.travelplanner.domain.ai.EmbeddingModelRef;
import com.travelplanner.domain.port.EmbeddingPort;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.List;

/**
 * The live embedding provider (ADR 010 §5 — {@code text-embedding-3-small}, 1536 dimensions).
 *
 * <p>{@code dimensions} is sent explicitly rather than left to the model's default. The
 * {@code text-embedding-3-*} family supports dimension reduction, so the API will happily return a
 * 512-dimensional vector if asked — and those vectors are silently incomparable with the
 * 1536-dimensional rows already in the index. Sending the pinned value makes the request itself
 * carry the pin, not just the configuration that built this object.
 *
 * <p>Each returned vector is re-checked against {@link #modelRef()} before it leaves. A provider that
 * ignored the request, or a model swapped underneath the same name, would otherwise be discovered
 * only as degraded search quality weeks later.
 */
final class OpenAiEmbeddingAdapter implements EmbeddingPort {

    private final OpenAiEmbeddingModel model;
    private final EmbeddingModelRef modelRef;

    OpenAiEmbeddingAdapter(AiProperties.Embeddings settings, String apiKey, Duration timeout) {
        this.modelRef = new EmbeddingModelRef(
                AiProperties.OPENAI_PROVIDER, settings.getModel(), settings.getDimension());
        this.model = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(settings.getModel())
                .dimensions(settings.getDimension())
                .timeout(timeout)
                .build();
    }

    @Override
    public EmbeddingModelRef modelRef() {
        return modelRef;
    }

    @Override
    public float[] embed(String text) {
        try {
            Response<Embedding> response = model.embed(text);
            return verified(response.content().vector());
        } catch (RuntimeException failure) {
            throw ProviderErrorMapper.map(AiProperties.OPENAI_PROVIDER, failure);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        try {
            Response<List<Embedding>> response =
                    model.embedAll(texts.stream().map(TextSegment::from).toList());
            return response.content().stream().map(embedding -> verified(embedding.vector())).toList();
        } catch (RuntimeException failure) {
            throw ProviderErrorMapper.map(AiProperties.OPENAI_PROVIDER, failure);
        }
    }

    private float[] verified(float[] vector) {
        if (vector.length != modelRef.dimension()) {
            throw com.travelplanner.domain.exception.AiProviderException.responseInvalid(
                    "openai returned a " + vector.length + "-dimensional vector, but the index is "
                            + "pinned to " + modelRef.dimension() + " (ADR 010 §5)");
        }
        return vector;
    }
}
