package com.travelplanner.domain.model;

import java.util.List;

/**
 * The unvalidated, string-shaped fields a model claims to have found in a traveller's message
 * (task 19; PLAN §6 item 3).
 *
 * <p><strong>An interface, not a record, and that is the whole point.</strong> The JSON the model
 * returns has to be bound by Jackson, and {@code domain/} may not import Jackson (ArchUnit
 * {@code domainIsFrameworkFree}). Declaring the <em>shape</em> here and letting the annotated
 * payload record in {@code ai/extraction/} implement it means the validation that turns this into a
 * {@link TripBriefDetails} lives in the domain — where the rules already are — while the wire
 * binding stays in the adapter. The alternative, a domain record copied field-by-field out of the
 * payload, is the same thirteen fields written twice, and the second copy is where a newly added
 * field gets forgotten.
 *
 * <p><strong>Every accessor may return {@code null}, an empty list, or nonsense.</strong> This is
 * model output: a date of {@code "next spring"}, a budget of {@code "-5"}, a party of thirty. None
 * of it has been through a domain invariant yet, which is exactly why nothing outside
 * {@link TripBriefExtraction#from} is allowed to read it — that method is the single door between
 * "the model said" and "the brief holds".
 *
 * <p>Dates are ISO {@code yyyy-MM-dd} strings and the budget is a decimal <em>string</em> rather
 * than a number. Binding money to a {@code double} would reintroduce the representation error
 * {@link com.travelplanner.domain.valueobject.Money} exists to prevent, at the one boundary where
 * the value is least trustworthy.
 */
public interface TripBriefDraft {

    /** Candidate destination slugs, most-preferred first. */
    List<String> destinations();

    /** UC-C1-05: the traveller asked to be surprised, so any destination above is discarded. */
    boolean surpriseMe();

    /** ISO {@code yyyy-MM-dd}, or {@code null}. Both ends are needed for a range. */
    String startDate();

    /** ISO {@code yyyy-MM-dd}, or {@code null}. */
    String endDate();

    /** A {@link com.travelplanner.domain.enums.DateFlexibility} name, or {@code null}. */
    String dateFlexibility();

    /** Free text, or {@code null}. */
    String departureCity();

    /** A decimal string such as {@code "4000.00"}, or {@code null}. Never a float. */
    String budgetAmount();

    /** An ISO 4217 code such as {@code "MYR"}, or {@code null}. */
    String budgetCurrency();

    /** Adults in the party, or {@code null}. */
    Integer adults();

    /** Children in the party, or {@code null}. */
    Integer children();

    /** {@link com.travelplanner.domain.enums.TravelInterest} names. */
    List<String> interests();

    /** A {@link com.travelplanner.domain.enums.TravelPace} name, or {@code null}. */
    String pace();

    /**
     * The {@link ClarificationQuestion#id()} values the model was <em>not</em> confident about.
     *
     * <p>This is how "ask rather than guess" (PLAN §3.1) survives contact with a model that is
     * fluent about everything. A value listed here is discarded even when it parses perfectly, so
     * "sometime in spring, I think" becomes the {@code travel_dates} question instead of a
     * confidently invented April.
     */
    List<String> ambiguousFields();
}
