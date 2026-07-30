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
 * Hybrid retrieval: the vector arm and the lexical arm, fused by reciprocal rank (ADR 010 §5).
 *
 * <h2>Why fusion is on rank and not on score</h2>
 *
 * <p>The two arms produce numbers that are not on the same scale and cannot be put on one. Cosine
 * similarity is bounded in {@code 0..1} and roughly calibrated — 0.8 means something like the same
 * thing for every query. {@code ts_rank} is unbounded and depends on how many lexemes matched, so its
 * absolute value means nothing across queries: a two-word query's best hit and a five-word query's
 * best hit are not comparable, let alone comparable to a cosine.
 *
 * <p>A weighted sum of the two therefore needs a normaliser per query, and any normaliser derived
 * from the returned rows (divide by the max, z-score, min-max) makes a document's score depend on
 * <em>which other documents came back</em>. Adding one irrelevant row changes the ranking of rows
 * that did not move. That class of instability is invisible in a demo and impossible to debug later.
 *
 * <p>Reciprocal rank fusion sidesteps it by discarding the scores and keeping only each arm's
 * ordering, which is the part both arms agree is meaningful:
 *
 * <pre>
 *   fused(d) = Σ over arms  1 / (K + rank_arm(d))          rank is 1-based; an arm that
 *                                                          did not return d contributes nothing
 * </pre>
 *
 * <p>The shape does the work. {@code 1/(K+rank)} falls off fast enough that a top-3 position in one
 * arm outweighs a mid-list position in both, and slowly enough that agreement between the arms still
 * wins ties — which is the behaviour wanted here, because a row both arms found is a row that is both
 * semantically close and literally on topic.
 *
 * <h2>What the returned relevance means</h2>
 *
 * <p>Raw RRF sums are tiny ({@code 2/(60+1) ≈ 0.033} at best), and {@link KnowledgeMatch} requires
 * {@code 0..1}, so the sum is divided by the best a document could possibly score — first place in
 * every arm consulted. That makes the number readable rather than merely legal:
 *
 * <table border="1">
 *   <caption>Normalised relevance for K = 60, two arms consulted</caption>
 *   <tr><th>Where the document ranked</th><th>relevance</th></tr>
 *   <tr><td>1st in both arms</td><td>1.00</td></tr>
 *   <tr><td>1st lexically, absent from the vector arm</td><td>0.50</td></tr>
 *   <tr><td>10th in both arms</td><td>0.87</td></tr>
 *   <tr><td>20th in one arm only</td><td>0.38</td></tr>
 * </table>
 *
 * <p>The divisor counts the arms <em>consulted</em>, not the arms that returned anything, so a
 * guide-only query — which has no lexical arm by design — does not have its scores inflated to look
 * like unanimous agreement.
 *
 * <h2>The floor stays inside the vector arm</h2>
 *
 * <p>{@link KnowledgeQuery#similarityFloor()} is applied in the vector arm's SQL and nowhere else. It
 * is stated here because the obvious-looking alternative is fatal: a 0.5 floor applied to a fused
 * relevance would drop every single-arm match — and a single-arm match is the only kind hybrid
 * retrieval can add over pure vector. The floor and the relevance are different quantities that
 * happen to share a range.
 */
@Component
@RequiresDatabase
public class KnowledgeHybridSearch {

    /**
     * RRF's rank-damping constant, the value the original paper uses and the one every mainstream
     * implementation kept.
     *
     * <p>It sets how sharply rank matters. At {@code K = 0} first place is worth twice second and
     * fusion becomes winner-take-all; as {@code K} grows all ranks converge and fusion becomes
     * "appeared in more arms". 60 sits where a strong single-arm hit can still outrank a mediocre
     * consensus, which is the trade this corpus needs — with three destinations and ten POIs each, an
     * arm's list is short and over-weighting consensus would just return whatever both arms had room
     * for.
     */
    static final int RANK_CONSTANT = 60;

    /**
     * Slugs are lowercase alphanumerics and hyphens; anything else is a bug or an attack and is
     * rejected rather than escaped.
     *
     * <p>Checked here, once, because both arms interpolate the slug into SQL rather than binding it —
     * the vector arm out of necessity (V18's partial indexes cannot be reached through a bind
     * parameter; see {@link KnowledgeVectorSearch}) and the lexical arm for consistency with it.
     */
    private static final Pattern SAFE_SLUG = Pattern.compile("^[a-z0-9-]{1,120}$");

    private final KnowledgeVectorSearch vectorSearch;
    private final KnowledgeFullTextSearch fullTextSearch;

    public KnowledgeHybridSearch(KnowledgeVectorSearch vectorSearch,
            KnowledgeFullTextSearch fullTextSearch) {
        this.vectorSearch = vectorSearch;
        this.fullTextSearch = fullTextSearch;
    }

    /**
     * Runs both arms and returns the fused top-k, most relevant first.
     *
     * <p>Sequentially, not concurrently. Two short indexed reads on one connection inside one
     * read-only transaction; parallelising them would need a second connection per search and the
     * pool is sized for one per request — the same arithmetic that made F-41 a deadlock.
     */
    public List<KnowledgeMatch> search(KnowledgeQuery query, String destinationSlug) {
        requireSafeSlug(destinationSlug);

        // Vector arm first, and the order is load-bearing rather than incidental: `fuse` breaks ties in
        // arm order, so where the two arms rank different rows first — each scoring 1/(K+1), which is the
        // common case when one row is semantically close and another is lexically exact — the semantic
        // hit leads. That is the right default for this product: ranking is built on semantic retrieval
        // and the lexical arm is the corrective, not the other way round.
        List<KnowledgeMatch> semantic = vectorSearch.search(query, destinationSlug);
        List<KnowledgeMatch> lexical = fullTextSearch.search(query, destinationSlug);

        // One arm consulted, not two, when the caller asked only for guides — see the class note on
        // the divisor. `types` is the question "was there a lexical arm to run", which is exactly
        // what KnowledgeFullTextSearch answers by returning an empty list.
        int armsConsulted = query.types().contains(KnowledgeMatchType.POI) ? 2 : 1;

        return fuse(List.of(semantic, lexical), armsConsulted, query.topK());
    }

    /**
     * Reciprocal rank fusion over any number of ranked lists.
     *
     * <p>Package-private and static so the arithmetic is testable without a database. The ranking
     * rule is the part of hybrid retrieval most likely to be "simplified" by someone who reads the
     * two SQL queries and assumes the merge is a sort.
     *
     * @param rankedArms each arm's results, already ordered best-first. An arm's list may be empty
     * @param armsConsulted how many arms were asked, which may exceed the number that answered
     */
    static List<KnowledgeMatch> fuse(List<List<KnowledgeMatch>> rankedArms, int armsConsulted,
            int topK) {

        // LinkedHashMap: iteration order is arm order then rank, so documents that tie on fused score
        // come out in the order the first arm ranked them. Ties are the norm on a small corpus, and a
        // HashMap would resolve them by hash of a random UUID — a ranking that changes between runs
        // for reasons nothing in the data explains.
        Map<MatchKey, Fused> byDocument = new LinkedHashMap<>();

        for (List<KnowledgeMatch> arm : rankedArms) {
            for (int index = 0; index < arm.size(); index++) {
                KnowledgeMatch match = arm.get(index);
                int rank = index + 1;
                byDocument.computeIfAbsent(MatchKey.of(match), key -> new Fused(match))
                        .add(1.0 / (RANK_CONSTANT + rank));
            }
        }

        double best = armsConsulted * (1.0 / (RANK_CONSTANT + 1));

        List<KnowledgeMatch> fused = new ArrayList<>(byDocument.size());
        for (Fused entry : byDocument.values()) {
            fused.add(entry.toMatch(best));
        }

        // Sorted descending by relevance. `Comparator.reversed()` on a stable sort preserves insertion
        // order within a tie — the LinkedHashMap order established above.
        fused.sort(Comparator.comparingDouble(KnowledgeMatch::relevance).reversed());
        return fused.size() <= topK ? List.copyOf(fused) : List.copyOf(fused.subList(0, topK));
    }

    /**
     * What makes two arms' hits the same document.
     *
     * <p>The id alone is not enough. {@code id} is a primary key within its own table, and a POI id
     * and a guide id are drawn from the same UUID space — colliding is vanishingly unlikely, but
     * keying on the pair costs nothing and removes the question. {@link KnowledgeMatch#sourceType()}
     * exists for exactly this reason.
     */
    private record MatchKey(KnowledgeMatchType sourceType, UUID id) {

        static MatchKey of(KnowledgeMatch match) {
            return new MatchKey(match.sourceType(), match.id());
        }
    }

    /**
     * One document's accumulating fused score.
     *
     * <p>Keeps the <em>first</em> arm's match as the representative rather than merging them. The
     * fields that could differ between arms are the snippet and the relevance; the snippet is built
     * from the same columns by both queries, and the relevance is being replaced. Everything that
     * identifies or cites the row — id, destination, provenance — comes from the same database row
     * either way, so there is nothing to reconcile and no reason to invent a merge rule.
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
            // Math.min guards the record's 0..1 invariant against floating-point drift: summing two
            // reciprocals and dividing by their sum can land a hair above 1.0, and KnowledgeMatch
            // would reject it. Clamping is right here and would be wrong in the record — a score out
            // of range from anywhere else is a bug worth failing on.
            double relevance = Math.min(1.0, score / best);
            return new KnowledgeMatch(
                    representative.sourceType(),
                    representative.id(),
                    representative.destinationId(),
                    representative.snippet(),
                    relevance,
                    representative.provenance());
        }
    }

    private static void requireSafeSlug(String slug) {
        if (slug == null || !SAFE_SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("unsafe destination slug: " + slug);
        }
    }
}
