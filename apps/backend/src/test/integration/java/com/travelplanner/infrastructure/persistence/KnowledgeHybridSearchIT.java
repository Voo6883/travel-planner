package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.domain.enums.KnowledgeMatchType;
import com.travelplanner.domain.model.KnowledgeMatch;
import com.travelplanner.domain.port.KnowledgePort;
import com.travelplanner.domain.valueobject.KnowledgeQuery;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Gate <b>17B</b>: hybrid retrieval, and the Definition of Done that says fusion must
 * <em>demonstrably beat pure vector</em> on {@code street food} and {@code temples}.
 *
 * <h2>How a synthetic embedding can prove anything</h2>
 *
 * <p>The embeddings here are uniform vectors, so "semantic similarity" is whatever the test decides it
 * is. That sounds like it makes the comparison meaningless, and it is in fact what makes it rigorous:
 * the test <em>constructs</em> the situation the ADR claims exists — a row whose text literally answers
 * the query while its embedding does not — and then asserts pure vector misses it and fusion does not.
 * Real embeddings would make the same point probabilistically and the test would be a coin flip on a
 * ten-row corpus.
 *
 * <p>The situation is not contrived. It is what "street food" does to an embedding: the phrase lands
 * near <em>food street</em>, <em>night market</em>, <em>casual dining</em> and a dozen other things, so
 * a row that says the words competes with everything that means roughly the same. That is the failure
 * ADR 010 §5 cites when it mandates fusion rather than offering it.
 *
 * <h2>Comparison is against the vector arm directly</h2>
 *
 * <p>{@link KnowledgeVectorSearch} is autowired alongside the port so "pure vector" is the real
 * production query rather than a re-implementation. The port itself is hybrid — that is the point of
 * the change — so there would otherwise be nothing to compare against.
 *
 * @see KnowledgeHybridSearchTest for the fusion arithmetic, which needs no database
 */
class KnowledgeHybridSearchIT extends AbstractPostgresIntegrationTest {

    private static final String SLUG = "tokyo-jp";

    private static final Instant FETCHED_AT = Instant.parse("2026-06-15T10:30:00Z");

    /** ADR 010 §5's floor. Used as-is, because the point is what happens at the real setting. */
    private static final double FLOOR = KnowledgeQuery.DEFAULT_SIMILARITY_FLOOR;

    @Autowired
    private KnowledgePort knowledge;

    @Autowired
    private KnowledgeVectorSearch vectorArm;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID sourceId;
    private UUID destinationId;

    @BeforeEach
    void seedOneDestination() {
        jdbc.execute("TRUNCATE knowledge_source, destination, travel_app CASCADE");

        sourceId = UUID.randomUUID();
        destinationId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO knowledge_source (id, source_ref, name, licence, attribution_text,
                        source_url, retrieved_at, trust_tier)
                VALUES (?, 'wikivoyage:tokyo', 'Wikivoyage', 'CC_BY_SA_4_0',
                        '© Wikivoyage contributors', 'https://en.wikivoyage.org/wiki/Tokyo', ?,
                        'COMMUNITY')
                """, sourceId, java.sql.Timestamp.from(FETCHED_AT));
        jdbc.update("""
                INSERT INTO destination (id, slug, name, country_code, timezone, coverage_level)
                VALUES (?, ?, 'Tokyo', 'JP', 'Asia/Tokyo', 'FULL')
                """, destinationId, SLUG);
    }

    // ---------------------------------------------------------------------------------------
    // The Definition of Done
    // ---------------------------------------------------------------------------------------

    /**
     * "street food" — the row that says the words is <em>invisible</em> to pure vector and present under
     * fusion. This is the Definition of Done's claim, stated exactly.
     *
     * <p>The stall's embedding sits at 0.30 similarity, below ADR 010 §5's 0.50 floor, so the vector arm
     * does not merely rank it low: it does not return it at all. No amount of reranking a vector-only
     * result set can recover a row that was never in it, which is the argument for a second arm rather
     * than for a better sort.
     *
     * <p><strong>Presence, not promotion.</strong> The two rows come back tied — each is first in one arm
     * and absent from the other, so reciprocal rank fusion has no basis for separating them and scores
     * both 0.5. An earlier version of this test asserted the stall came <em>first</em>; that was wishful
     * rather than derived, and the arithmetic said otherwise. What fusion buys here is recall, and
     * overstating it as ranking would have made the test a claim the implementation does not support.
     */
    @Test
    void hybridFindsTheStreetFoodStallThatPureVectorDropsBelowTheFloor() {
        UUID stall = insertPoi("night-street-food", "Sample Night Street Food Alley",
                "Charcoal grills and noodle counters along one lane.", 0.30f);
        UUID garden = insertPoi("quiet-garden", "Sample Quiet Garden",
                "A walled garden with a tea house.", 0.95f);

        KnowledgeQuery query = poiQuery("street food", 0.95f);

        assertThat(vectorArm.search(query, SLUG))
                .describedAs("pure vector cannot see the stall at all — the floor removed it")
                .extracting(KnowledgeMatch::id)
                .containsExactly(garden);

        assertThat(knowledge.search(query))
                .describedAs("fusion makes it reachable, which pure vector cannot be tuned into doing")
                .extracting(KnowledgeMatch::id)
                .containsExactlyInAnyOrder(stall, garden);
        assertThat(knowledge.search(query))
                .describedAs("first in one arm, absent from the other — tied by construction")
                .allSatisfy(match -> assertThat(match.relevance()).isEqualTo(0.5));
    }

    /**
     * When both arms rank a different row first, the semantic one is listed first.
     *
     * <p>A tie has to break somewhere, and the rule is "the order the arms were consulted", with the
     * vector arm first. Stated as a test because it is otherwise an accident of
     * {@link KnowledgeHybridSearch}'s loop that a later refactor would silently invert — and because the
     * choice is defensible on its merits: semantic retrieval is what this product ranks on and the
     * lexical arm is the corrective, so where the two disagree completely the semantic hit leads.
     */
    @Test
    void aTieBetweenTheArmsIsBrokenTowardsTheSemanticHit() {
        insertPoi("night-street-food", "Sample Night Street Food Alley",
                "Charcoal grills and noodle counters along one lane.", 0.30f);
        UUID semantic = insertPoi("quiet-garden", "Sample Quiet Garden",
                "A walled garden with a tea house.", 0.95f);

        assertThat(knowledge.search(poiQuery("street food", 0.95f)))
                .extracting(KnowledgeMatch::id)
                .startsWith(semantic);
    }

    /**
     * "temples" — the query V15's {@code simple} configuration could not answer.
     *
     * <p>The POI says "Temple", singular, and nowhere says "temples". Under {@code simple} — no stemming
     * — {@code websearch_to_tsquery('simple','temples')} does not match it, so the lexical arm returns
     * nothing and hybrid degrades silently to pure vector. V22 rebuilt the index with {@code english};
     * this is the test that would have caught the original.
     *
     * <p>Worth noting why nobody caught it: the sample seed's temple POI carries the plural in its
     * <em>description</em> as well as the singular in its name, so the same assertion written against
     * seeded data passes either way. Only a fixture that deliberately omits the plural distinguishes the
     * two configurations.
     */
    @Test
    void hybridMatchesTemplesAgainstASingularTempleName() {
        UUID temple = insertPoi("old-town-temple", "Sample Old Town Temple",
                "A wooden hall reached by a stone stair.", 0.20f);
        insertPoi("ramen-counter", "Sample Ramen Counter", "Eight seats and one broth.", 0.95f);

        KnowledgeQuery query = poiQuery("temples", 0.95f);

        assertThat(knowledge.search(query))
                .describedAs("plural query against a singular name — this needs stemming, which "
                        + "V15's `simple` configuration does not do")
                .extracting(KnowledgeMatch::id)
                .contains(temple);
    }

    /** The same stemming, the other way round: a singular query against a plural name. */
    @Test
    void stemmingWorksInBothDirections() {
        UUID markets = insertPoi("craft-markets", "Sample Craft Markets",
                "Two covered rows of stalls.", 0.10f);

        assertThat(knowledge.search(poiQuery("market", 0.95f)))
                .extracting(KnowledgeMatch::id)
                .contains(markets);
    }

    // ---------------------------------------------------------------------------------------
    // Properties the fusion has to keep
    // ---------------------------------------------------------------------------------------

    /**
     * A row both arms find outranks a row only one arm finds.
     *
     * <p>The behaviour that makes fusion more than a union. Both POIs match the query lexically; only
     * one is also semantically close, and it wins.
     */
    @Test
    void agreementBetweenTheArmsOutranksASingleArmHit() {
        UUID both = insertPoi("street-food-market", "Sample Street Food Market",
                "Stalls and counters under one roof.", 0.95f);
        UUID lexicalOnly = insertPoi("street-food-lane", "Sample Street Food Lane",
                "A narrow lane of stalls.", 0.10f);

        List<KnowledgeMatch> matches = knowledge.search(poiQuery("street food", 0.95f));

        assertThat(matches).extracting(KnowledgeMatch::id).startsWith(both).contains(lexicalOnly);
        assertThat(matches.get(0).relevance()).isGreaterThan(matches.get(1).relevance());
    }

    /**
     * The similarity floor, asserted where it is still a similarity.
     *
     * <p>{@code KnowledgePersistenceIT} used to prove this through the port by reading two returned
     * scores, averaging them and feeding the result back as a floor. That worked while the port returned
     * cosine similarities and stopped meaning anything when it started returning fused ranks — the
     * mistake {@link com.travelplanner.domain.model.KnowledgeMatch}'s javadoc now warns about, made by
     * the codebase itself. The floor is a vector-arm property, so it is asserted against the vector arm,
     * where {@code relevance} really is a cosine similarity.
     */
    @Test
    void theFloorRemovesWeakSemanticMatchesFromTheVectorArm() {
        UUID near = insertPoi("near", "Sample Near Match", "Close in embedding space.", 0.95f);
        insertPoi("far", "Sample Far Match", "Distant in embedding space.", 0.30f);

        List<KnowledgeMatch> unfiltered = vectorArm.search(new KnowledgeQuery(destinationId,
                "zzzz-no-lexical-match", uniformVector(0.95f), KnowledgeQuery.DEFAULT_TOP_K, 0.0,
                Set.of(KnowledgeMatchType.POI)), SLUG);

        assertThat(unfiltered).hasSize(2);
        assertThat(unfiltered.get(0).relevance()).isGreaterThan(unfiltered.get(1).relevance());
        // tiltedVector puts the stored vectors at their nominal cosine similarity, so 0.30 really is
        // below ADR 010 §5's 0.50 rather than approximately below it.
        assertThat(unfiltered.get(1).relevance()).isLessThan(FLOOR);

        assertThat(vectorArm.search(new KnowledgeQuery(destinationId, "zzzz-no-lexical-match",
                uniformVector(0.95f), KnowledgeQuery.DEFAULT_TOP_K, FLOOR,
                Set.of(KnowledgeMatchType.POI)), SLUG))
                .describedAs("the floor removes rather than reorders")
                .extracting(KnowledgeMatch::id)
                .containsExactly(near);
    }

    /**
     * The similarity floor still removes weak <em>semantic</em> matches.
     *
     * <p>Adding a lexical arm must not quietly disable the floor. A row that is neither semantically
     * close nor lexically matched stays out — otherwise "hybrid" would just mean "return more".
     */
    @Test
    void aRowThatNeitherArmMatchesStaysOut() {
        insertPoi("unrelated", "Sample Ferry Terminal", "Departures every twenty minutes.", 0.20f);
        UUID relevant = insertPoi("street-food-lane", "Sample Street Food Lane",
                "A narrow lane of stalls.", 0.95f);

        assertThat(knowledge.search(poiQuery("street food", 0.95f)))
                .extracting(KnowledgeMatch::id)
                .containsExactly(relevant);
    }

    /**
     * The lexical arm is scoped to one destination.
     *
     * <p>{@code ix_poi_fulltext} is not partial, so unlike the vector arm nothing about index selection
     * forces the filter to be present — which makes it exactly the kind of predicate that gets dropped.
     * ADR 010 §5's pre-filter rule is about never letting one city's rows compete for another's top-k,
     * and a lexical arm that ignored it would return Bangkok stalls for a Tokyo query.
     */
    @Test
    void theLexicalArmDoesNotLeakAcrossDestinations() {
        insertPoi("street-food-lane", "Sample Street Food Lane", "A narrow lane of stalls.", 0.95f);

        UUID osaka = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO destination (id, slug, name, country_code, timezone, coverage_level)
                VALUES (?, 'osaka-jp', 'Osaka', 'JP', 'Asia/Tokyo', 'FULL')
                """, osaka);

        assertThat(knowledge.search(new KnowledgeQuery(osaka, "street food", uniformVector(0.95f),
                KnowledgeQuery.DEFAULT_TOP_K, FLOOR, Set.of(KnowledgeMatchType.POI))))
                .describedAs("Osaka has no POIs; Tokyo's must not answer for it")
                .isEmpty();
    }

    /**
     * A lexical hit carries real provenance, not a placeholder.
     *
     * <p>The lexical arm assembles its own projection by hand — a second copy of the eight-column
     * provenance join — so it is a second place the citation can be got wrong. PLAN §4.1.0 makes an
     * uncitable result useless, and a match with the wrong licence is worse than no match.
     */
    @Test
    void aLexicalOnlyHitIsStillFullyCitable() {
        insertPoi("street-food-lane", "Sample Street Food Lane", "A narrow lane of stalls.", 0.10f);

        assertThat(knowledge.search(poiQuery("street food", 0.95f))).singleElement()
                .satisfies(match -> {
                    assertThat(match.provenance().sourceRef()).isEqualTo("wikivoyage:tokyo");
                    assertThat(match.provenance().attributionText()).isEqualTo("© Wikivoyage contributors");
                    // The ROW's fetch time, not the source's — the F-32 provenance trap, on this path too.
                    assertThat(match.provenance().retrievedAt()).isEqualTo(FETCHED_AT);
                    assertThat(match.snippet()).contains("Sample Street Food Lane");
                });
    }

    /**
     * A POI with no embedding row is still reachable.
     *
     * <p>Task 40 re-embeds asynchronously, so between a curation edit and the next embedding run a POI
     * legitimately has text and no vector. Under pure vector such a row is unreachable — it is not in
     * {@code poi_embedding}, so no query can return it. The lexical arm reads {@code poi} directly,
     * which makes newly-curated content findable immediately instead of after the next embed.
     */
    @Test
    void aPoiWithNoEmbeddingYetIsStillFindableLexically() {
        UUID unembedded = insertPoiWithoutEmbedding("new-stall", "Sample Street Food Stall",
                "Added by curation, not yet embedded.");

        assertThat(knowledge.search(poiQuery("street food", 0.95f)))
                .extracting(KnowledgeMatch::id)
                .containsExactly(unembedded);
    }

    /**
     * A guide-only query runs no lexical arm and says so through its scores.
     *
     * <p>The lexical arm is POI-only by design ({@link KnowledgeFullTextSearch} states why). What must
     * not happen is a guide-only query scoring its top hit as though two arms had agreed on it — the
     * divisor counts arms consulted, so a single-arm top hit is 1.0 and means "best of the one thing we
     * asked", not "unanimous".
     */
    @Test
    void aGuideOnlyQueryIsVectorOnlyAndScoredAgainstOneArm() {
        UUID guideId = insertEmbeddedGuide(0.95f);

        assertThat(knowledge.search(new KnowledgeQuery(destinationId, "street food",
                uniformVector(0.95f), KnowledgeQuery.DEFAULT_TOP_K, FLOOR,
                Set.of(KnowledgeMatchType.GUIDE))))
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.id()).isEqualTo(guideId);
                    assertThat(match.sourceType()).isEqualTo(KnowledgeMatchType.GUIDE);
                    assertThat(match.relevance()).isEqualTo(1.0);
                });
    }

    /** Nothing matched by either arm is an empty list, not a low-scoring one. */
    @Test
    void nothingRelevantReturnsNothing() {
        insertPoi("ferry", "Sample Ferry Terminal", "Departures every twenty minutes.", 0.10f);

        assertThat(knowledge.search(poiQuery("temples", 0.95f))).isEmpty();
    }

    /**
     * Arbitrary chat text cannot break the lexical query.
     *
     * <p>{@code websearch_to_tsquery} is used rather than {@code to_tsquery} precisely because this text
     * arrives from a chat message. {@code to_tsquery} would raise a syntax error on any of these, which
     * would surface as a 500 on a user typing normally.
     */
    @Test
    void punctuationHeavyUserTextDoesNotThrow() {
        insertPoi("street-food-lane", "Sample Street Food Lane", "A narrow lane of stalls.", 0.95f);

        for (String text : List.of("street food!!", "where's the \"street food\"?", "street & food",
                "food | street", "!!!", "street food -garden", ":)", "temples (old)")) {
            assertThat(knowledge.search(poiQuery(text, 0.95f)))
                    .describedAs("query text: %s", text)
                    .isNotNull();
        }
    }

    // ---------------------------------------------------------------------------------------

    private KnowledgeQuery poiQuery(String text, float similarity) {
        return new KnowledgeQuery(destinationId, text, uniformVector(similarity),
                KnowledgeQuery.DEFAULT_TOP_K, FLOOR, Set.of(KnowledgeMatchType.POI));
    }

    /**
     * A POI plus an embedding whose cosine similarity against {@link #uniformVector} of 0.95 is
     * controlled by {@code similarity}.
     *
     * <p>Two uniform vectors of the same sign have cosine similarity 1.0 regardless of magnitude, so the
     * similarity is varied by tilting one component rather than by scaling. See {@link #tiltedVector}.
     */
    private UUID insertPoi(String slug, String name, String description, float similarity) {
        UUID poiId = insertPoiWithoutEmbedding(slug, name, description);
        jdbc.update("""
                INSERT INTO poi_embedding (id, poi_id, destination_id, destination_slug, embedding,
                        embedding_model, embedding_dimension, content_hash)
                VALUES (?, ?, ?, ?, CAST(? AS vector), 'text-embedding-3-small', 1536, ?)
                """, UUID.randomUUID(), poiId, destinationId, SLUG, tiltedVector(similarity),
                contentHash(slug));
        return poiId;
    }

    private UUID insertPoiWithoutEmbedding(String slug, String name, String description) {
        UUID poiId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO poi (id, destination_id, slug, name, description, category, source_id,
                        retrieved_at)
                VALUES (?, ?, ?, ?, ?, 'FOOD', ?, ?)
                """, poiId, destinationId, slug, name, description, sourceId,
                java.sql.Timestamp.from(FETCHED_AT));
        return poiId;
    }

    private UUID insertEmbeddedGuide(float similarity) {
        UUID guideId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO destination_guide (id, destination_id, locale, overview, food, practical,
                        source_id, retrieved_at, version)
                VALUES (?, ?, 'en', 'An overview of street food and gardens.', 'Street food is the point.',
                        'Cash is still useful.', ?, ?, 0)
                """, guideId, destinationId, sourceId, java.sql.Timestamp.from(FETCHED_AT));
        jdbc.update("""
                INSERT INTO destination_guide_embedding (id, guide_id, destination_id, destination_slug,
                        field_group, embedding, embedding_model, embedding_dimension, content_hash)
                VALUES (?, ?, ?, ?, 'FOOD', CAST(? AS vector), 'text-embedding-3-small', 1536, ?)
                """, UUID.randomUUID(), guideId, destinationId, SLUG, tiltedVector(similarity),
                contentHash("guide-food"));
        return guideId;
    }

    /**
     * A 1536-dimension query vector: {@code value} in the first component, zero elsewhere.
     *
     * <p>Not all-zeros — cosine distance is undefined for a zero vector and pgvector returns NaN, which
     * sorts unpredictably and produced a genuinely confusing failure the first time it was tried.
     */
    private static String uniformVectorLiteral(float value) {
        StringBuilder literal = new StringBuilder("[").append(value);
        literal.append(",0".repeat(KnowledgeQuery.EMBEDDING_DIMENSION - 1));
        return literal.append(']').toString();
    }

    private static float[] uniformVector(float value) {
        float[] vector = new float[KnowledgeQuery.EMBEDDING_DIMENSION];
        vector[0] = value;
        return vector;
    }

    /**
     * A stored vector whose cosine similarity against {@link #uniformVector} is approximately
     * {@code target}.
     *
     * <p>The query vector points along the first axis, so cosine similarity with {@code (a, b, 0, …)} is
     * {@code a / sqrt(a² + b²)}. Setting {@code a = target} and {@code b = sqrt(1 - target²)} gives a
     * unit vector at exactly that similarity — so the fixture's "0.30" really is below ADR 010 §5's 0.50
     * floor rather than approximately below it, which is what the assertions depend on.
     */
    private static String tiltedVector(float target) {
        double orthogonal = Math.sqrt(Math.max(0.0, 1.0 - (double) target * target));
        StringBuilder literal = new StringBuilder("[").append(target).append(',').append(orthogonal);
        literal.append(",0".repeat(KnowledgeQuery.EMBEDDING_DIMENSION - 2));
        return literal.append(']').toString();
    }

    /** {@code content_hash} is {@code char(64)} and unique per row; the value is never read here. */
    private static String contentHash(String seed) {
        String hex = Integer.toHexString(seed.hashCode());
        return "0".repeat(64 - hex.length()) + hex;
    }
}
