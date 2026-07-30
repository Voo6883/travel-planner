package com.travelplanner.infrastructure.persistence;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.KnowledgeMatchType;
import com.travelplanner.domain.enums.TrustTier;
import com.travelplanner.domain.model.KnowledgeMatch;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.domain.valueobject.KnowledgeQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The lexical arm of hybrid retrieval (ADR 010 §5) — Postgres full-text search over POIs.
 *
 * <h2>Why a lexical arm exists at all</h2>
 *
 * <p>ADR 010 §5 mandates fusion rather than offering it, and names the reason: the plan's own worked
 * examples, <em>street food</em> and <em>temples</em>, are lexical. An embedding places "street food"
 * near "food street", "night market" and "casual dining" — which is the behaviour you want when the
 * user is describing a mood and exactly not what you want when they typed a category name. Cosine
 * similarity has no way to express "this row literally says the words you asked for", so no amount of
 * tuning the vector arm produces it.
 *
 * <h2>POIs only, and why that is a design choice rather than a shortcut</h2>
 *
 * <p>The vector arm searches POIs and guide chunks. This one searches POIs alone.
 *
 * <p>Lexical retrieval's advantage is on <strong>short, name-like text</strong>, where an embedding
 * has too few tokens to disambiguate and blurs distinct things together. A POI is a name plus a
 * sentence. A destination guide is long-form narrative, which is the case embeddings are good at and
 * where a keyword hit says little — "street food" appears in every guide's food section, so a lexical
 * arm over guides would rank all three destinations' guides top for that query while telling the
 * caller nothing it did not already know.
 *
 * <p>There is also a structural reason. ADR 010 §5 embeds a guide as three chunks, one per
 * {@code field_group}, so the vector arm returns matches keyed by {@code (GUIDE, guide_id)} with the
 * matched section as the snippet. A lexical match against the {@code destination_guide} row is per
 * <em>row</em>, not per section, so fusing the two would collide two different snippets on one key.
 * Doing it properly means a field-group-aware lexical query and a decision about how sections compete
 * with each other — a guide-retrieval tuning question that belongs with whoever owns guide ranking,
 * not smuggled in beside a POI change.
 *
 * <h2>The configuration is load-bearing</h2>
 *
 * <p>{@link #TEXT_SEARCH_CONFIG} must be the same configuration V22's index was built with. Not for
 * correctness — a mismatched configuration still returns rows, having quietly stopped using the index
 * and stemmed the query differently from the corpus. It fails as degraded recall on a large corpus,
 * which is the failure mode nobody notices. V22's header carries the measurement showing why it is
 * {@code english} and not V15's {@code simple}.
 *
 * <h2>No similarity floor here</h2>
 *
 * <p>{@link KnowledgeQuery#similarityFloor()} is not applied, and must not be. It is a cosine floor:
 * {@code @@} is a predicate, not a distance — a row either contains the query's lexemes or it does
 * not, so there is no low-quality tail to trim. Worse, applying a 0.5 similarity floor to lexical
 * hits would delete precisely the rows fusion exists to surface: a row the vector arm ranked below
 * the floor, or did not return at all, is the only kind of row this arm can add. A floor here would
 * make hybrid retrieval mathematically incapable of beating pure vector.
 */
@Component
@RequiresDatabase
public class KnowledgeFullTextSearch {

    /**
     * The text-search configuration, matching V22's index.
     *
     * <p>A constant rather than a literal in the SQL because "these two must agree" is the whole
     * point, and a constant is something a test can assert about.
     */
    static final String TEXT_SEARCH_CONFIG = "english";

    /**
     * How many lexical candidates to fetch.
     *
     * <p>The same {@code topK} as the vector arm. Reciprocal-rank fusion only reads a document's
     * <em>rank</em> within each arm, so an arm that returns fewer candidates than the other
     * contributes proportionally less — matching the depths keeps neither arm structurally
     * advantaged.
     */
    private final int candidateDepth;

    @PersistenceContext
    private EntityManager entityManager;

    KnowledgeFullTextSearch() {
        this(KnowledgeQuery.DEFAULT_TOP_K);
    }

    KnowledgeFullTextSearch(int candidateDepth) {
        this.candidateDepth = candidateDepth;
    }

    /**
     * Lexical matches for the query text, best first.
     *
     * <p>{@code relevance} on the returned matches is {@code ts_rank}, normalised — see
     * {@link #normalise}. It is not comparable with the vector arm's cosine similarity and is never
     * compared with it: {@link KnowledgeHybridSearch} fuses on rank precisely so the two scales never
     * have to be reconciled.
     *
     * @param destinationSlug interpolated, having been checked by the caller. Unlike the vector arm
     *     this is not required for index selection — {@code ix_poi_fulltext} is not partial — but the
     *     destination filter is still applied in SQL, because ADR 010 §5's pre-filter rule is about
     *     never letting one destination's rows compete with another's for the top-k
     */
    public List<KnowledgeMatch> search(KnowledgeQuery query, String destinationSlug) {
        if (!query.types().contains(KnowledgeMatchType.POI)) {
            // Guides are vector-only by design; a guide-only request has no lexical arm to run.
            return List.of();
        }

        // `websearch_to_tsquery`, not `plainto_tsquery` or `to_tsquery`. It is the only one of the
        // three that accepts arbitrary user text without throwing: `to_tsquery` requires operator
        // syntax and raises a syntax error on a bare phrase, and this text comes from a chat message.
        // It also gives the user quoted phrases and `-word` for free, which is what a search box is
        // expected to do.
        String sql = """
                SELECT p.id, p.destination_id,
                       p.name || CASE WHEN p.description IS NULL THEN '' ELSE ' — ' || p.description END,
                       ts_rank(to_tsvector('%1$s',
                                   coalesce(p.name, '') || ' ' || coalesce(p.description, '')),
                               websearch_to_tsquery('%1$s', :text)),
                       ks.source_ref, ks.name, ks.licence, ks.attribution_text, ks.source_url,
                       ks.trust_tier, p.retrieved_at
                FROM poi p
                JOIN destination d ON d.id = p.destination_id
                JOIN knowledge_source ks ON ks.id = p.source_id
                WHERE d.slug = '%2$s'
                  AND to_tsvector('%1$s',
                          coalesce(p.name, '') || ' ' || coalesce(p.description, ''))
                      @@ websearch_to_tsquery('%1$s', :text)
                ORDER BY 4 DESC, p.id
                LIMIT :topK
                """.formatted(TEXT_SEARCH_CONFIG, destinationSlug);

        // `ORDER BY 4 DESC, p.id` — p.id breaks ties. ts_rank ties are common on a small corpus (two
        // POIs matching one lexeme each score identically), and an unordered tie makes the fused
        // ranking depend on Postgres' row order, which is not stable across vacuums. A test that
        // asserts a ranking needs the ranking to be a function of the data.
        return toMatches(runQuery(sql, query));
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> runQuery(String sql, KnowledgeQuery query) {
        return entityManager.createNativeQuery(sql)
                .setParameter("text", query.text())
                .setParameter("topK", candidateDepth)
                .getResultList();
    }

    private static List<KnowledgeMatch> toMatches(List<Object[]> rows) {
        List<KnowledgeMatch> matches = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            matches.add(new KnowledgeMatch(
                    KnowledgeMatchType.POI,
                    (UUID) row[0],
                    (UUID) row[1],
                    (String) row[2],
                    normalise(((Number) row[3]).doubleValue()),
                    new KnowledgeProvenance(
                            (String) row[4],
                            (String) row[5],
                            KnowledgeLicence.valueOf((String) row[6]),
                            (String) row[7],
                            (String) row[8],
                            TrustTier.valueOf((String) row[9]),
                            (Instant) row[10])));
        }
        return matches;
    }

    /**
     * Squashes {@code ts_rank} into {@code 0.0..1.0} so it satisfies {@link KnowledgeMatch}.
     *
     * <p>{@code ts_rank} has no upper bound — it grows with term frequency and the number of matched
     * lexemes — so it cannot be handed to a record that requires a unit interval. {@code r / (1 + r)}
     * is monotonic, which is the only property that matters: fusion reads rank, so the mapping must
     * preserve order and nothing else. A linear rescale would need a maximum that depends on the
     * corpus and would therefore change the number for a row whose text never changed.
     */
    private static double normalise(double rank) {
        return rank / (1.0 + rank);
    }
}
