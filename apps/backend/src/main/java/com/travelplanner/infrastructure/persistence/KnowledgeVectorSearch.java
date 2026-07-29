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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Hybrid semantic retrieval over the TKB (ADR 010 §5).
 *
 * <h2>Why this is native SQL</h2>
 *
 * <p>Neither JPQL nor the Criteria API can express pgvector's {@code <=>} cosine-distance operator,
 * and that operator is not a detail — it is the only thing an HNSW index can answer. A JPA query
 * would silently fall back to a sequential scan and a different similarity definition. There is
 * consequently no {@code @Entity} for {@code poi_embedding} or {@code destination_guide_embedding}
 * at all: Hibernate cannot map a {@code vector(1536)} column, and inventing a converter would buy
 * an object model for rows nothing traverses.
 *
 * <h2>Why the destination slug is inlined rather than bound</h2>
 *
 * <p>This one is easy to get subtly wrong. V18 creates one <em>partial</em> HNSW index per
 * destination, so that the destination filter is applied before the ANN search (ADR 010 §5 —
 * post-filtering discards the index's global top-k and a small destination can come back empty).
 * Postgres will only use a partial index when it can prove the query predicate implies the index
 * predicate, and with a bind parameter it cannot: {@code destination_slug = $1} does not imply
 * {@code destination_slug = 'tokyo-jp'} under a generic plan. Binding the slug would therefore
 * disable the very index the migration exists to provide, and nothing would fail — recall would
 * just quietly degrade.
 *
 * <p>The slug is consequently interpolated as a literal, and {@link #SAFE_SLUG} is what makes that
 * safe. Slugs originate from our own {@code destination} table and are constrained to lowercase
 * alphanumerics and hyphens; anything else is rejected outright rather than escaped. The embedding
 * model name is a compile-time constant for the same planner reason.
 *
 * <p><strong>Unverified until seeding.</strong> With the tables empty, {@code EXPLAIN} reports a
 * sequential scan whatever the query looks like, so index usage cannot be confirmed yet. Task 17
 * seeds the corpus; the first thing to check afterwards is that {@code EXPLAIN ANALYZE} on this
 * query names {@code ix_poi_embedding_hnsw_<destination>}.
 */
@Component
@RequiresDatabase
public class KnowledgeVectorSearch {

    /**
     * ADR 010 §5 pins the model, and V18's index predicates name this exact string. A mismatch
     * would miss every index without failing.
     */
    static final String PINNED_MODEL = "text-embedding-3-small";

    /**
     * Slugs are lowercase alphanumerics and hyphens. Enforced rather than escaped: a slug that does
     * not match is a bug or an attack, and neither deserves a best-effort quoting attempt.
     */
    private static final Pattern SAFE_SLUG = Pattern.compile("^[a-z0-9-]{1,120}$");

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Runs the query against the requested match types and returns the union, most similar first.
     *
     * <p>Ordering across the two tables happens here rather than in SQL. A {@code UNION ALL} over
     * both would need a shared column list and would rank a guide chunk against a POI on raw cosine
     * distance alone; merging in memory keeps the two queries readable and each one able to use its
     * own partial index. Both sides are already bounded by {@code topK}, so this sorts at most
     * {@code 2 * topK} rows.
     */
    public List<KnowledgeMatch> search(KnowledgeQuery query, String destinationSlug) {
        requireSafeSlug(destinationSlug);

        List<KnowledgeMatch> matches = new ArrayList<>();
        if (query.types().contains(KnowledgeMatchType.POI)) {
            matches.addAll(runPoiSearch(query, destinationSlug));
        }
        if (query.types().contains(KnowledgeMatchType.GUIDE)) {
            matches.addAll(runGuideSearch(query, destinationSlug));
        }

        return matches.stream()
                .sorted(Comparator.comparingDouble(KnowledgeMatch::score).reversed())
                .limit(query.topK())
                .toList();
    }

    private List<KnowledgeMatch> runPoiSearch(KnowledgeQuery query, String slug) {
        // `1 - (embedding <=> v)` converts cosine DISTANCE to cosine SIMILARITY, which is what the
        // similarity floor and KnowledgeMatch.score are expressed in. Getting the direction wrong
        // would invert the ranking while still returning plausible-looking rows.
        String sql = """
                SELECT pe.poi_id, p.destination_id,
                       p.name || CASE WHEN p.description IS NULL THEN '' ELSE ' — ' || p.description END,
                       1 - (pe.embedding <=> CAST(:embedding AS vector)),
                       ks.source_ref, ks.name, ks.licence, ks.attribution_text, ks.source_url,
                       ks.trust_tier, p.retrieved_at
                FROM poi_embedding pe
                JOIN poi p ON p.id = pe.poi_id
                JOIN knowledge_source ks ON ks.id = p.source_id
                WHERE pe.destination_slug = '%s'
                  AND pe.embedding_model = '%s'
                  AND 1 - (pe.embedding <=> CAST(:embedding AS vector)) >= :floor
                ORDER BY pe.embedding <=> CAST(:embedding AS vector)
                LIMIT :topK
                """.formatted(slug, PINNED_MODEL);

        return toMatches(runQuery(sql, query), KnowledgeMatchType.POI);
    }

    private List<KnowledgeMatch> runGuideSearch(KnowledgeQuery query, String slug) {
        String sql = """
                SELECT ge.guide_id, g.destination_id,
                       CASE ge.field_group
                            WHEN 'OVERVIEW' THEN g.overview
                            WHEN 'FOOD' THEN COALESCE(g.food, '')
                            ELSE COALESCE(g.practical, '')
                       END,
                       1 - (ge.embedding <=> CAST(:embedding AS vector)),
                       ks.source_ref, ks.name, ks.licence, ks.attribution_text, ks.source_url,
                       ks.trust_tier, g.retrieved_at
                FROM destination_guide_embedding ge
                JOIN destination_guide g ON g.id = ge.guide_id
                JOIN knowledge_source ks ON ks.id = g.source_id
                WHERE ge.destination_slug = '%s'
                  AND ge.embedding_model = '%s'
                  AND 1 - (ge.embedding <=> CAST(:embedding AS vector)) >= :floor
                ORDER BY ge.embedding <=> CAST(:embedding AS vector)
                LIMIT :topK
                """.formatted(slug, PINNED_MODEL);

        return toMatches(runQuery(sql, query), KnowledgeMatchType.GUIDE);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> runQuery(String sql, KnowledgeQuery query) {
        return entityManager.createNativeQuery(sql)
                .setParameter("embedding", toVectorLiteral(query.embedding()))
                .setParameter("floor", query.similarityFloor())
                .setParameter("topK", query.topK())
                .getResultList();
    }

    private static List<KnowledgeMatch> toMatches(List<Object[]> rows, KnowledgeMatchType type) {
        List<KnowledgeMatch> matches = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            matches.add(new KnowledgeMatch(
                    type,
                    (UUID) row[0],
                    (UUID) row[1],
                    (String) row[2],
                    toDouble(row[3]),
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
     * pgvector's driver-side representation is a bracketed list, so the vector is passed as text and
     * cast in SQL. {@code Float.toString} rather than a formatter: a locale that writes {@code 0,5}
     * would produce a literal Postgres cannot parse, and it would only fail on machines set to that
     * locale.
     */
    private static String toVectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder(embedding.length * 12 + 2);
        literal.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(embedding[i]));
        }
        return literal.append(']').toString();
    }

    /** The driver returns the computed similarity as {@code Double} or {@code BigDecimal}. */
    private static double toDouble(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        return ((Number) value).doubleValue();
    }

    private static void requireSafeSlug(String slug) {
        if (slug == null || !SAFE_SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("unsafe destination slug: " + slug);
        }
    }
}
