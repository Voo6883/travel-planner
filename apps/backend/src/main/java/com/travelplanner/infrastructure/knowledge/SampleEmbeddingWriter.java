package com.travelplanner.infrastructure.knowledge;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.ai.EmbeddingModelRef;
import com.travelplanner.domain.enums.GuideFieldGroup;
import com.travelplanner.domain.port.EmbeddingPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes {@code poi_embedding} and {@code destination_guide_embedding} rows for seeded content
 * (ADR 010 §5).
 *
 * <h2>Native SQL, and no entity</h2>
 *
 * <p>Hibernate cannot map {@code vector(1536)}, so V18's two embedding tables have no {@code @Entity}
 * at all — the same reason {@code KnowledgeVectorSearch} reads them with native SQL. This class
 * writes them with {@link JdbcTemplate} rather than an {@code EntityManager}, because an
 * {@code EntityManager} would put {@code jakarta.persistence} outside
 * {@code infrastructure.persistence} and break {@code LayerRulesTest}'s JPA confinement rule for a
 * statement that gains nothing from JPA. Parameters are bound rather than interpolated: unlike the
 * read path, a write has no partial index to convince the planner about.
 *
 * <h2>{@code content_hash} is the re-embed trigger</h2>
 *
 * <p>ADR 010 §5: unchanged rows are never re-embedded. The hash is SHA-256 over the <em>exact</em>
 * text handed to the embedder, so re-running the seeder over unmodified files costs one
 * {@code SELECT} per chunk and no provider call. Task 40 must reproduce {@link #poiChunk} byte for
 * byte or its first run will re-embed the whole corpus.
 *
 * <h2>Why the model column says {@code text-embedding-3-small} even under the stub</h2>
 *
 * <p>V18 pins the string in a CHECK constraint and in every partial HNSW index predicate, so it is
 * the only value the table accepts. The active {@link EmbeddingPort} in a default checkout is
 * {@code StubEmbeddingAdapter}, which reports itself as {@code stub-embedding-v1} precisely so stub
 * vectors stay distinguishable — and writing the pinned string here loses that distinction at the
 * row level.
 *
 * <p>That is a knowing trade, and it is why sample destinations are seeded {@code PARTIAL}. Stub
 * vectors are unit-length, deterministic and mutually meaningless: their similarities are
 * reproducible but carry no semantic signal, so they are not comparable with vectors from the real
 * model. {@code PARTIAL} keeps every row they describe out of C2 ranking (ADR 010 §4), and the
 * README records that every sample row needs re-embedding on task 40's first real run. The
 * alternative — writing {@code stub-embedding-v1} — is rejected by the CHECK constraint, so the
 * choice is between this and not exercising the embedding tables at all.
 */
@Component
@Profile({"dev", "docker", "local"})
@ConditionalOnProperty(
        prefix = "travelplanner.knowledge.sample-seed", name = "enabled", havingValue = "true")
@RequiresDatabase
public class SampleEmbeddingWriter {

    /** V18's CHECK constraints and index predicates accept no other value. */
    static final String PINNED_MODEL = EmbeddingModelRef.PINNED_MODEL;

    /**
     * ADR 010 §5's chunking version, bumped when the chunking or prompt changes while the model does
     * not. The seeder never bumps it; task 40 owns that.
     */
    private static final int EMBEDDING_VERSION = 1;

    private static final String SELECT_GUIDE_HASH = """
            SELECT content_hash FROM destination_guide_embedding
            WHERE guide_id = ? AND field_group = ? AND embedding_model = ?
            """;

    private static final String INSERT_GUIDE = """
            INSERT INTO destination_guide_embedding (
                id, guide_id, destination_id, destination_slug, field_group, embedding,
                embedding_model, embedding_dimension, embedding_version, content_hash, embedded_at)
            VALUES (?, ?, ?, ?, ?, CAST(? AS vector), ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_GUIDE = """
            UPDATE destination_guide_embedding
            SET embedding = CAST(? AS vector), embedding_dimension = ?, embedding_version = ?,
                content_hash = ?, embedded_at = ?
            WHERE guide_id = ? AND field_group = ? AND embedding_model = ?
            """;

    private static final String SELECT_POI_HASH = """
            SELECT content_hash FROM poi_embedding
            WHERE poi_id = ? AND embedding_model = ?
            """;

    private static final String INSERT_POI = """
            INSERT INTO poi_embedding (
                id, poi_id, destination_id, destination_slug, embedding,
                embedding_model, embedding_dimension, embedding_version, content_hash, embedded_at)
            VALUES (?, ?, ?, ?, CAST(? AS vector), ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_POI = """
            UPDATE poi_embedding
            SET embedding = CAST(? AS vector), embedding_dimension = ?, embedding_version = ?,
                content_hash = ?, embedded_at = ?
            WHERE poi_id = ? AND embedding_model = ?
            """;

    private final JdbcTemplate jdbc;
    private final EmbeddingPort embeddings;

    public SampleEmbeddingWriter(JdbcTemplate jdbc, EmbeddingPort embeddings) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        int dimension = embeddings.modelRef().dimension();
        if (dimension != EmbeddingModelRef.PINNED_DIMENSION) {
            // Failing here beats failing per row: `vector(1536)` and V18's CHECK would reject every
            // insert, several hundred rows into a seed, with a message about a column rather than
            // about the configured model.
            throw new IllegalStateException("The configured embedding model "
                    + embeddings.modelRef().qualifiedName() + " produces " + dimension
                    + "-dimensional vectors, but the knowledge embedding tables are pinned to "
                    + EmbeddingModelRef.PINNED_DIMENSION + " (ADR 010 §5)");
        }
    }

    /**
     * Embeds one guide field group, unless an identical chunk is already stored.
     *
     * @param text the section's text; {@code null} or blank writes nothing, because an absent
     *     section is honest and an empty vector retrieves as noise
     * @return {@code true} when a vector was written
     */
    public boolean writeGuideChunk(
            UUID guideId,
            UUID destinationId,
            String destinationSlug,
            GuideFieldGroup fieldGroup,
            String text,
            Instant embeddedAt) {

        if (text == null || text.isBlank()) {
            return false;
        }
        String hash = sha256(text);
        List<String> stored = jdbc.query(SELECT_GUIDE_HASH,
                (row, index) -> row.getString(1), guideId, fieldGroup.name(), PINNED_MODEL);
        if (!stored.isEmpty() && hash.equals(stored.get(0))) {
            return false;
        }

        String vector = toVectorLiteral(embeddings.embed(text));
        OffsetDateTime at = OffsetDateTime.ofInstant(embeddedAt, ZoneOffset.UTC);
        if (stored.isEmpty()) {
            jdbc.update(INSERT_GUIDE, UUID.randomUUID(), guideId, destinationId, destinationSlug,
                    fieldGroup.name(), vector, PINNED_MODEL, EmbeddingModelRef.PINNED_DIMENSION,
                    EMBEDDING_VERSION, hash, at);
        } else {
            jdbc.update(UPDATE_GUIDE, vector, EmbeddingModelRef.PINNED_DIMENSION, EMBEDDING_VERSION,
                    hash, at, guideId, fieldGroup.name(), PINNED_MODEL);
        }
        return true;
    }

    /**
     * Embeds one POI as a single chunk, unless an identical chunk is already stored.
     *
     * @return {@code true} when a vector was written
     */
    public boolean writePoiChunk(
            UUID poiId,
            UUID destinationId,
            String destinationSlug,
            String text,
            Instant embeddedAt) {

        String hash = sha256(text);
        List<String> stored = jdbc.query(SELECT_POI_HASH,
                (row, index) -> row.getString(1), poiId, PINNED_MODEL);
        if (!stored.isEmpty() && hash.equals(stored.get(0))) {
            return false;
        }

        String vector = toVectorLiteral(embeddings.embed(text));
        OffsetDateTime at = OffsetDateTime.ofInstant(embeddedAt, ZoneOffset.UTC);
        if (stored.isEmpty()) {
            jdbc.update(INSERT_POI, UUID.randomUUID(), poiId, destinationId, destinationSlug,
                    vector, PINNED_MODEL, EmbeddingModelRef.PINNED_DIMENSION, EMBEDDING_VERSION,
                    hash, at);
        } else {
            jdbc.update(UPDATE_POI, vector, EmbeddingModelRef.PINNED_DIMENSION, EMBEDDING_VERSION,
                    hash, at, poiId, PINNED_MODEL);
        }
        return true;
    }

    /**
     * ADR 010 §5: a POI embeds {@code name + description + tags} as <em>one</em> chunk, because a
     * POI is short enough that splitting it produces fragments with no standalone meaning.
     *
     * <p>The exact concatenation is part of the seed contract — {@code content_hash} is taken over
     * this string, so task 40 reproducing it differently would re-embed every POI on its first run.
     * Absent parts are skipped rather than rendered as {@code "null"}.
     */
    static String poiChunk(String name, String description, List<String> tags) {
        StringBuilder chunk = new StringBuilder(name == null ? "" : name);
        if (description != null && !description.isBlank()) {
            chunk.append('\n').append(description);
        }
        if (tags != null && !tags.isEmpty()) {
            chunk.append('\n').append(String.join(", ", tags));
        }
        return chunk.toString();
    }

    /** Lowercase hex SHA-256 — 64 characters, which is exactly what {@code content_hash} holds. */
    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every JVM", impossible);
        }
    }

    /**
     * pgvector's text form is a bracketed list, cast to {@code vector} in SQL.
     *
     * <p>{@code Float.toString} rather than a formatter, for the reason
     * {@code KnowledgeVectorSearch} documents on its own copy of this: a locale that writes
     * {@code 0,5} would produce a literal Postgres cannot parse, and it would only fail on machines
     * set to that locale. The duplication is deliberate — sharing it would mean either a public
     * method on the read path or a utility class that exists for six lines.
     */
    static String toVectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder(embedding.length * 12 + 2);
        literal.append('[');
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(embedding[index]));
        }
        return literal.append(']').toString();
    }
}
