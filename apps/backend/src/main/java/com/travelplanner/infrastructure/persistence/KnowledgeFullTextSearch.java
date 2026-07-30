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
 * <p>POIs only, deliberately; why, and why the arm exists at all, are in
 * {@code docs/KNOWLEDGE-SCHEMA.md} §5.2.
 */
@Component
@RequiresDatabase
public class KnowledgeFullTextSearch {

    /**
     * Must name the same configuration V22's index was built with.
     *
     * <p>A mismatch does not fail. It stops using the index and stems the query differently from the
     * corpus, which shows up as degraded recall on a corpus large enough for nobody to notice.
     */
    static final String TEXT_SEARCH_CONFIG = "english";

    /** Matched to the vector arm's depth, so neither arm is structurally advantaged in the fusion. */
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
     * <p>{@link KnowledgeQuery#similarityFloor()} is deliberately not applied: it is a cosine floor, and
     * {@code @@} is a predicate with no low-quality tail to trim. Applying it here would drop the rows
     * the vector arm ranked below the floor — the only rows this arm can add.
     */
    public List<KnowledgeMatch> search(KnowledgeQuery query, String destinationSlug) {
        if (!query.types().contains(KnowledgeMatchType.POI)) {
            return List.of();
        }

        // `websearch_to_tsquery` is the only one of the three that accepts arbitrary user text without
        // throwing — `to_tsquery` raises a syntax error on a bare phrase, and this comes from a chat
        // message. `ORDER BY 4 DESC, p.id`: ts_rank ties are common, and an unbroken tie makes the
        // ranking depend on Postgres' row order, which is not stable across vacuums.
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
     * Squashes unbounded {@code ts_rank} into {@code 0..1} for {@link KnowledgeMatch}.
     *
     * <p>Monotonic is the only property required — fusion reads rank, not value. A linear rescale would
     * need a corpus-dependent maximum, so a row's number would change though its text had not.
     */
    private static double normalise(double rank) {
        return rank / (1.0 + rank);
    }
}
