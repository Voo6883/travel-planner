package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The three things about {@code KnowledgeVectorSearch}'s SQL that make V18's partial HNSW indexes
 * reachable at all (ADR 010 §5).
 *
 * <p>A source-text assertion, which is unusual and deliberate. What needs protecting is not behaviour —
 * the query returns the same rows either way — but the <em>shape</em> that lets PostgreSQL prove the
 * query implies an index predicate. Break it and nothing fails: correct results, a sequential scan over
 * every embedding, discovered whenever the corpus grows enough to hurt.
 *
 * <p>It reads the file rather than running the query for the same reason it lives in the unit suite:
 * a plan is not a property of the SQL alone. See {@link #partialIndexUsageWasMeasuredNotAsserted}.
 *
 * <h2>What was measured</h2>
 *
 * <p>Closing the third gap in <b>F-32</b> — "are the partial HNSW indexes actually used by the planner"
 * — on PostgreSQL 16.6 with pgvector 0.8.1, against a schema migrated from empty:
 *
 * <table border="1">
 *   <caption>Plan chosen for the production query shape</caption>
 *   <tr><th>{@code poi_embedding} rows</th><th>heap pages</th><th>plan</th></tr>
 *   <tr><td>100</td><td>2</td><td>{@code Seq Scan}</td></tr>
 *   <tr><td>600</td><td>~30</td><td>{@code Seq Scan}</td></tr>
 *   <tr><td>2,000</td><td>25–53</td><td>{@code Index Scan using ix_poi_embedding_hnsw_tokyo}</td></tr>
 *   <tr><td>20,000</td><td>247</td><td>{@code Index Scan using ix_poi_embedding_hnsw_tokyo}</td></tr>
 * </table>
 *
 * <p><b>The mechanism works, and it is not currently engaged.</b> ADR 010 §1's curation floor is ≥25
 * POIs per destination — roughly 75 embeddings across the three covered cities, two orders of magnitude
 * below the crossover. A 1536-dimension vector is TOASTed, so a small embedding table is a couple of
 * heap pages and reading all of it genuinely beats descending a graph; the planner is right to refuse
 * the index today. The six indexes are insurance for a corpus that does not exist yet, and they cost
 * write and build time now. That is the number to weigh if somebody proposes dropping them.
 */
class KnowledgeVectorSearchContractTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/com/travelplanner/infrastructure/persistence/KnowledgeVectorSearch.java");

    /**
     * The slug must stay a literal.
     *
     * <p>{@code destination_slug = $1} does not provably imply {@code destination_slug = 'tokyo-jp'},
     * so a bound slug cannot be counted on to reach a per-destination partial index. String
     * interpolation into SQL is exactly what a later reviewer "fixes", which is why this is a test and
     * not only a comment — and why {@code SAFE_SLUG} exists to make the interpolation safe rather than
     * to make it unnecessary.
     */
    @Test
    void theDestinationSlugIsInterpolatedAsALiteralRatherThanBound() {
        assertThat(source())
                .contains("WHERE pe.destination_slug = '%s'")
                .contains("WHERE ge.destination_slug = '%s'");
    }

    /** The pinned model is half of every index predicate, so it is a literal for the same reason. */
    @Test
    void thePinnedEmbeddingModelIsALiteralToo() {
        assertThat(source())
                .contains("AND pe.embedding_model = '%s'")
                .contains("AND ge.embedding_model = '%s'");
    }

    /**
     * HNSW serves an ordered, limited nearest-neighbour scan and nothing else.
     *
     * <p>Drop the {@code ORDER BY … <=> …} or the {@code LIMIT} and the index becomes unusable however
     * the predicates are written — a filter on a distance expression is not a nearest-neighbour search.
     */
    @Test
    void theQueryIsAnOrderedLimitedNearestNeighbourScan() {
        String source = source();

        assertThat(source).contains("ORDER BY pe.embedding <=> CAST(:embedding AS vector)");
        assertThat(source).contains("ORDER BY ge.embedding <=> CAST(:embedding AS vector)");
        assertThat(source).containsIgnoringCase("LIMIT");
    }

    /**
     * The query vector is bound, and that is fine — unlike the slug.
     *
     * <p>Worth pinning because the asymmetry looks arbitrary: the slug is interpolated and the vector is
     * not. The difference is that the vector appears only in the {@code ORDER BY} distance expression,
     * which no index predicate mentions, so binding it costs nothing. Interpolating it would put 1536
     * caller-supplied floats into a SQL string for no benefit.
     */
    @Test
    void theQueryVectorIsBoundBecauseNoIndexPredicateMentionsIt() {
        assertThat(source()).contains("CAST(:embedding AS vector)");
    }

    /**
     * Why there is no test here that runs {@code EXPLAIN}.
     *
     * <p>There was one, in the integration suite, and it was withdrawn. A chosen plan is not a property
     * of the SQL — it is a property of the SQL <em>plus</em> row counts, heap pages, index state, and
     * custom-versus-generic plan selection. In a suite that shares one database across ten test classes,
     * the last three are not controllable: the same query at the same 2,000 rows was observed choosing
     * a sequential scan and an index scan in different runs, differing only in what had run before it.
     *
     * <p>Two ways to force determinism were rejected. {@code enable_seqscan = off} proves the index is
     * <em>usable</em>, which was never in question, not that the planner <em>chooses</em> it. A
     * dedicated Gradle task nobody runs is the same as no test, with maintenance.
     *
     * <p>So the plan question was answered by measurement — recorded in this class's javadoc, and in
     * {@code docs/KNOWLEDGE-SCHEMA.md} §5 — and what is asserted here is the part that is genuinely a
     * property of the source: the query shape those measurements were taken against. This test exists to
     * say that out loud, so the absence reads as a decision.
     */
    @Test
    void partialIndexUsageWasMeasuredNotAsserted() {
        assertThat(source())
                .describedAs("the SAFE_SLUG guard is what makes literal interpolation defensible; if it "
                        + "goes, the interpolation has to go with it")
                .contains("SAFE_SLUG");
    }

    private static String source() {
        try {
            return Files.readString(SOURCE).replace("\r\n", "\n");
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + SOURCE, unreadable);
        }
    }
}
