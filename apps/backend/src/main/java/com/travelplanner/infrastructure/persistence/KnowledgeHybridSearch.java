package com.travelplanner.infrastructure.persistence;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.KnowledgeMatchType;
import com.travelplanner.domain.model.KnowledgeMatch;
import com.travelplanner.domain.valueobject.KnowledgeQuery;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The vector arm and the lexical arm, fused by reciprocal rank (ADR 010 §5).
 *
 * <p>Fusion is on rank because the arms' scores are not on one scale and cannot be put on one — why
 * that rules out every weighted sum, what {@code relevance} then means, and the measured claim behind
 * it are in {@code docs/KNOWLEDGE-SCHEMA.md} §5.2.
 */
@Component
@RequiresDatabase
public class KnowledgeHybridSearch {

    /**
     * RRF's rank-damping constant — the original paper's value.
     *
     * <p>At 0 fusion is winner-take-all; as it grows fusion becomes "appeared in more arms". 60 lets a
     * strong single-arm hit still outrank a mediocre consensus, which a ten-POI corpus needs.
     */
    static final int RANK_CONSTANT = 60;

    /** Both arms interpolate the slug into SQL rather than binding it; see {@link KnowledgeVectorSearch}. */
    private static final Pattern SAFE_SLUG = Pattern.compile("^[a-z0-9-]{1,120}$");

    private final KnowledgeVectorSearch vectorSearch;
    private final KnowledgeFullTextSearch fullTextSearch;

    public KnowledgeHybridSearch(KnowledgeVectorSearch vectorSearch,
            KnowledgeFullTextSearch fullTextSearch) {
        this.vectorSearch = vectorSearch;
        this.fullTextSearch = fullTextSearch;
    }

    /**
     * Both arms, fused, most relevant first.
     *
     * <p>Sequentially: parallelising would need a second connection per search from a pool sized for
     * one per request — the arithmetic that made F-41 a deadlock.
     */
    public List<KnowledgeMatch> search(KnowledgeQuery query, String destinationSlug) {
        requireSafeSlug(destinationSlug);

        // Vector arm first is load-bearing: `fuse` breaks ties in arm order, and two arms ranking
        // different rows first is the common case. The semantic hit leads.
        List<KnowledgeMatch> semantic = vectorSearch.search(query, destinationSlug);
        List<KnowledgeMatch> lexical = fullTextSearch.search(query, destinationSlug);

        // Arms consulted, not arms that answered: a guide-only query has no lexical arm, and dividing
        // by 1 there keeps a top hit's 1.0 from reading as agreement between two.
        int armsConsulted = query.types().contains(KnowledgeMatchType.POI) ? 2 : 1;

        return fuse(List.of(semantic, lexical), armsConsulted, query.topK());
    }

    /**
     * Reciprocal rank fusion: {@code Σ 1 / (K + rank)}, normalised by first-place-in-every-arm.
     *
     * <p>Static and package-private so the arithmetic is testable without a database.
     *
     * @param armsConsulted how many arms were asked, which may exceed the number that answered
     */
    static List<KnowledgeMatch> fuse(List<List<KnowledgeMatch>> rankedArms, int armsConsulted,
            int topK) {

        // LinkedHashMap, not HashMap: ties are the norm on a small corpus, and a hash order would break
        // them by a random UUID — a ranking that changes between runs with nothing in the data to explain it.
        Map<MatchKey, Fused> byDocument = new LinkedHashMap<>();

        for (List<KnowledgeMatch> arm : rankedArms) {
            for (int index = 0; index < arm.size(); index++) {
                KnowledgeMatch match = arm.get(index);
                byDocument.computeIfAbsent(MatchKey.of(match), key -> new Fused(match))
                        .add(1.0 / (RANK_CONSTANT + index + 1));
            }
        }

        double best = armsConsulted * (1.0 / (RANK_CONSTANT + 1));
        List<KnowledgeMatch> fused = new ArrayList<>(byDocument.size());
        for (Fused entry : byDocument.values()) {
            fused.add(entry.toMatch(best));
        }

        fused.sort(Comparator.comparingDouble(KnowledgeMatch::relevance).reversed());
        return fused.size() <= topK ? List.copyOf(fused) : List.copyOf(fused.subList(0, topK));
    }

    /** Keyed on the pair: a POI id and a guide id are drawn from the same UUID space. */
    private record MatchKey(KnowledgeMatchType sourceType, UUID id) {

        static MatchKey of(KnowledgeMatch match) {
            return new MatchKey(match.sourceType(), match.id());
        }
    }

    /**
     * One document's accumulating fused score.
     *
     * <p>Keeps the first arm's match as the representative. Both arms build the snippet from the same
     * columns and everything identifying the row comes from the same database row, so there is nothing
     * to reconcile.
     */
    private static final class Fused {

        private final KnowledgeMatch representative;
        private double score;

        Fused(KnowledgeMatch representative) {
            this.representative = representative;
        }

        void add(double contribution) {
            score += contribution;
        }

        KnowledgeMatch toMatch(double best) {
            // Clamped here, not in the record: summing two reciprocals and dividing by their sum can
            // land a hair above 1.0, while an out-of-range value from anywhere else is worth failing on.
            return new KnowledgeMatch(
                    representative.sourceType(),
                    representative.id(),
                    representative.destinationId(),
                    representative.snippet(),
                    Math.min(1.0, score / best),
                    representative.provenance());
        }
    }

    private static void requireSafeSlug(String slug) {
        if (slug == null || !SAFE_SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("unsafe destination slug: " + slug);
        }
    }
}
