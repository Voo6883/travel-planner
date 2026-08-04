package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.TripBriefExtractionOutcome;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What one natural-language extraction attempt produced (task 19, UC-C1-02, UC-C1-05).
 *
 * <p><strong>The model proposes; this class decides.</strong> {@link #from} is the only door
 * between a {@link TripBriefDraft} and a {@link TripBriefDetails}, and everything that crosses it
 * is put through the same value objects a form submission goes through — {@link Money},
 * {@link DateRange}, {@link PartySize}, and {@code TripBriefDetails}' own normalisation. A budget
 * of {@code -5} and a party of thirty are refused here by {@link Money} and {@link PartySize}
 * themselves rather than by a second, model-specific rule set, so there is no way for the two to
 * drift apart: extraction is not a privileged write path.
 *
 * <p><strong>A refused value becomes a question, never a guess.</strong> Anything the model flagged
 * as uncertain, and anything that failed an invariant, is dropped and its field is left absent — at
 * which point {@link ClarificationNeeded#forDetails} asks about it, because that method is already
 * the definition of what a brief still needs (PLAN §3.1, §4.1.3). No parallel clarification model
 * is introduced, and there is no path that fills a missing budget, date, or departure city with an
 * invented value.
 *
 * <p><strong>Nothing here is persisted as prose.</strong> The record carries structured fields and
 * a prompt version; the model's own sentences are never stored (PLAN §5 item 5).
 *
 * @param details the fields that survived validation, merged onto whatever the brief already held.
 *     A field the model did not mention keeps its stored value — an extraction is additive, so a
 *     follow-up message about the budget cannot silently erase the dates. UC-C1-05's
 *     {@code surprise_me} flag lives here too, because the same {@code TripBriefDetails} is what the
 *     existing save path persists
 * @param unresolvedFields question ids the extraction refused to fill, plus
 *     {@link #FIELD_DESTINATIONS} when a destination list failed its own bound. Informational — the
 *     authoritative list of what is still needed is {@link #clarification()}
 * @param failureCode the {@code snake_case} AI error code when {@code outcome} is
 *     {@link TripBriefExtractionOutcome#FALLBACK}, otherwise {@code null}. It lets the caller say
 *     "the assistant timed out, here is the form" rather than presenting an unexplained blank form
 * @param promptVersion the {@code id@vN} label of the prompt that produced this, carried so a
 *     regression can be traced to the text that caused it (PLAN §5.3)
 */
public record TripBriefExtraction(
        TripBriefDetails details,
        List<String> unresolvedFields,
        TripBriefExtractionOutcome outcome,
        String failureCode,
        String promptVersion) {

    /**
     * The pseudo-field reported when a destination list is refused. It is not a clarification
     * question id, because destination preference is deliberately not asked about — an empty
     * preference is a legitimate answer that lets C2 rank the whole covered set.
     */
    public static final String FIELD_DESTINATIONS = "destinations";

    /** The only ids {@link TripBriefDraft#ambiguousFields()} may name; anything else is dropped. */
    private static final Set<String> QUESTION_IDS = Set.of(
            ClarificationNeeded.QUESTION_TRAVEL_DATES,
            ClarificationNeeded.QUESTION_DATE_FLEXIBILITY,
            ClarificationNeeded.QUESTION_DEPARTURE_CITY,
            ClarificationNeeded.QUESTION_BUDGET_MAX,
            ClarificationNeeded.QUESTION_PARTY_SIZE,
            ClarificationNeeded.QUESTION_INTERESTS,
            ClarificationNeeded.QUESTION_PACE);

    public TripBriefExtraction {
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(outcome, "outcome");
        unresolvedFields = unresolvedFields == null ? List.of() : List.copyOf(unresolvedFields);
        promptVersion = promptVersion == null ? "" : promptVersion;
    }

    /**
     * Validates a model draft against the domain and merges what survives onto {@code known}.
     *
     * @param known the brief's current details, or {@code null} for a brand-new brief. Values the
     *     model did not produce are carried through untouched
     */
    public static TripBriefExtraction from(TripBriefDraft draft, TripBriefDetails known,
            String promptVersion) {
        Objects.requireNonNull(draft, "draft");
        Refusals refusals = new Refusals(flaggedIds(draft));
        TripBriefDetails details = known == null ? TripBriefDetails.empty() : known;

        details = applyDestinations(details, draft, refusals);
        details = applyDates(details, draft, refusals);
        details = applyParty(details, draft, refusals);
        details = applyPreferences(details, draft, refusals);
        details = details.withSurpriseMe(draft.surpriseMe());
        return new TripBriefExtraction(details, refusals.rejected,
                TripBriefExtractionOutcome.EXTRACTED, null, promptVersion);
    }

    /**
     * The deterministic outcome when the model could not be used at all.
     *
     * <p>The brief is returned untouched, so the caller persists nothing new and the user sees the
     * ordinary intake form with every outstanding question on it. That is the whole of the fallback
     * behaviour: there is no partial parse, no salvaged half-object, and no retry loop above this
     * point (PLAN §4.1 — "no confident result" is a valid typed outcome).
     */
    public static TripBriefExtraction fallback(TripBriefDetails known, String failureCode,
            String promptVersion) {
        return new TripBriefExtraction(known == null ? TripBriefDetails.empty() : known,
                List.of(), TripBriefExtractionOutcome.FALLBACK, failureCode, promptVersion);
    }

    /** UC-C1-05 flag, exposed from the same details object that is persisted. */
    public boolean surpriseMe() {
        return details.surpriseMe();
    }

    /** What the brief still needs — task 18's rule, unchanged and unduplicated. */
    public ClarificationNeeded clarification() {
        return ClarificationNeeded.forDetails(details);
    }

    /** True when nothing is outstanding, i.e. the brief would reach {@code BRIEF_COMPLETE}. */
    public boolean isComplete() {
        return clarification().isSatisfied();
    }

    /**
     * The same extraction with the named destination slugs removed.
     *
     * <p>Used for the coverage refusal (ADR 010 §4): a destination the knowledge base never curated
     * cannot be researched honestly, and the application layer — the only layer that can see
     * {@code KnowledgePort} — strips it here rather than letting the persist step reject the whole
     * extraction. Every other field the traveller supplied survives.
     */
    public TripBriefExtraction withoutDestinations(List<String> removed) {
        if (removed == null || removed.isEmpty()) {
            return this;
        }
        List<String> kept = details.destinations().stream()
                .filter(slug -> !removed.contains(slug))
                .toList();
        return new TripBriefExtraction(details.withDestinations(kept), unresolvedFields,
                outcome, failureCode, promptVersion);
    }

    private static TripBriefDetails applyDestinations(TripBriefDetails details, TripBriefDraft draft,
            Refusals refusals) {
        if (draft.surpriseMe()) {
            // UC-C1-05: an open destination is an empty list plus the flag. A model that names
            // somewhere anyway is contradicting the traveller, and the traveller wins.
            return details.withDestinations(List.of());
        }
        List<String> slugs = refusals.accept(FIELD_DESTINATIONS,
                () -> TripBriefDetails.empty().withDestinations(draft.destinations()).destinations());
        return slugs == null || slugs.isEmpty() ? details : details.withDestinations(slugs);
    }

    private static TripBriefDetails applyDates(TripBriefDetails details, TripBriefDraft draft,
            Refusals refusals) {
        DateRange dates = refusals.accept(ClarificationNeeded.QUESTION_TRAVEL_DATES,
                () -> dateRangeOf(draft));
        TripBriefDetails updated = dates == null ? details : details.withDates(dates);
        DateFlexibility flexibility = refusals.accept(ClarificationNeeded.QUESTION_DATE_FLEXIBILITY,
                () -> constantOf(DateFlexibility.class, draft.dateFlexibility()));
        return flexibility == null ? updated : updated.withDateFlexibility(flexibility);
    }

    private static TripBriefDetails applyParty(TripBriefDetails details, TripBriefDraft draft,
            Refusals refusals) {
        PartySize party = refusals.accept(ClarificationNeeded.QUESTION_PARTY_SIZE,
                () -> partyOf(draft));
        TripBriefDetails updated = party == null ? details : details.withParty(party);
        Money budget = refusals.accept(ClarificationNeeded.QUESTION_BUDGET_MAX, () -> moneyOf(draft));
        return budget == null ? updated : updated.withBudget(budget);
    }

    private static TripBriefDetails applyPreferences(TripBriefDetails details, TripBriefDraft draft,
            Refusals refusals) {
        String city = refusals.accept(ClarificationNeeded.QUESTION_DEPARTURE_CITY,
                () -> TripBriefDetails.empty().withDepartureCity(draft.departureCity()).departureCity());
        TripBriefDetails updated = city == null ? details : details.withDepartureCity(city);
        List<TravelInterest> interests = refusals.accept(ClarificationNeeded.QUESTION_INTERESTS,
                () -> interestsOf(draft));
        if (interests != null && !interests.isEmpty()) {
            updated = updated.withInterests(interests);
        }
        TravelPace pace = refusals.accept(ClarificationNeeded.QUESTION_PACE,
                () -> constantOf(TravelPace.class, draft.pace()));
        return pace == null ? updated : updated.withPace(pace);
    }

    /** {@code null} when either end is missing; throws when either end is not a date, or reversed. */
    private static DateRange dateRangeOf(TripBriefDraft draft) {
        if (isBlank(draft.startDate()) || isBlank(draft.endDate())) {
            return null;
        }
        return DateRange.of(LocalDate.parse(draft.startDate().trim()),
                LocalDate.parse(draft.endDate().trim()));
    }

    /**
     * A party needs at least the adult count. Children alone is not a smaller answer, it is a party
     * {@link PartySize} refuses — and refusing it here means the question gets asked.
     */
    private static PartySize partyOf(TripBriefDraft draft) {
        if (draft.adults() == null && draft.children() == null) {
            return null;
        }
        return new PartySize(draft.adults() == null ? 0 : draft.adults(),
                draft.children() == null ? 0 : draft.children());
    }

    /** An amount with no currency is not money (PLAN §4.0.2-A), so both or neither. */
    private static Money moneyOf(TripBriefDraft draft) {
        if (isBlank(draft.budgetAmount()) || isBlank(draft.budgetCurrency())) {
            return null;
        }
        return Money.of(draft.budgetAmount().trim(), draft.budgetCurrency().trim());
    }

    /**
     * Unknown interest names are dropped individually; a list of nothing but unknowns is refused so
     * the question is asked rather than answered with silence.
     */
    private static List<TravelInterest> interestsOf(TripBriefDraft draft) {
        List<String> raw = draft.interests();
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        List<TravelInterest> resolved = new ArrayList<>();
        for (String name : raw) {
            TravelInterest interest = optionalConstant(TravelInterest.class, name);
            if (interest != null && !resolved.contains(interest)) {
                resolved.add(interest);
            }
        }
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("no recognised interest in " + raw.size() + " values");
        }
        return resolved;
    }

    private static <E extends Enum<E>> E constantOf(Class<E> vocabulary, String value) {
        if (isBlank(value)) {
            return null;
        }
        return Enum.valueOf(vocabulary, value.trim().toUpperCase(Locale.ROOT));
    }

    private static <E extends Enum<E>> E optionalConstant(Class<E> vocabulary, String value) {
        try {
            return constantOf(vocabulary, value);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static Set<String> flaggedIds(TripBriefDraft draft) {
        List<String> flagged = draft.ambiguousFields();
        if (flagged == null) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String raw : flagged) {
            if (raw == null) {
                continue;
            }
            String id = raw.trim().toLowerCase(Locale.ROOT);
            if (QUESTION_IDS.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Collects the fields the extraction would not fill.
     *
     * <p>Mutable and short-lived, which is the point: threading a growing list through eight static
     * helpers as an extra parameter would put every one of them over PLAN §4.0.4's three-parameter
     * limit, and the alternative — returning a pair from each — is eight throwaway record types.
     */
    private static final class Refusals {

        private final Set<String> flagged;
        private final List<String> rejected = new ArrayList<>();

        private Refusals(Set<String> flagged) {
            this.flagged = flagged;
        }

        /**
         * @return the parsed value, or {@code null} when the model said nothing, flagged the field
         *     as uncertain, or produced something the domain refuses. All three cases mean the same
         *     thing downstream: the field stays absent and gets asked about
         */
        private <T> T accept(String field, Supplier<T> parse) {
            if (flagged.contains(field)) {
                rejected.add(field);
                return null;
            }
            try {
                return parse.get();
            } catch (RuntimeException invalid) {
                // Every domain invariant signals with an unchecked exception — ValidationFailedException
                // from the value objects, DateTimeParseException from a date that is not one. Catching
                // them here is what turns "the model produced nonsense" into "the user is asked".
                rejected.add(field);
                return null;
            }
        }
    }
}
