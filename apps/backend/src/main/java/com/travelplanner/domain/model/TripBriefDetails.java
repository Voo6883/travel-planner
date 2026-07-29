package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Every field of a {@link TripBrief} a user or the agent may edit — the aggregate's identity,
 * version, and audit columns removed.
 *
 * <p>It exists so that a whole-body {@code PUT} and a single clarification answer are the same
 * operation on the same type. Without it, {@code PUT .../brief} would set eight fields through
 * eight chained {@code withX} calls on the aggregate and
 * {@code POST .../brief/actions/answer-clarification} would set one, and the field a later task
 * adds would have to be threaded through both paths — the shape in which one of them gets
 * forgotten. Here there is one place a field can be added and one place it can be validated.
 *
 * <p><strong>Every field is nullable or empty by design.</strong> A brief under construction is
 * incomplete, and PLAN §4.1.3 answers incompleteness with typed clarification rather than a
 * rejected save. What is <em>present</em> must still be valid: a negative budget or a reversed date
 * range is rejected by the value object itself, because "I have not decided yet" and "I entered
 * something impossible" are different facts and only the first one may be stored.
 *
 * @param destinations candidate destination slugs, most-preferred first. Empty means "no
 *        preference" — a legitimate answer that lets C2 rank the whole covered set. Whether a slug
 *        is actually covered is checked in the application layer, which is the only layer that can
 *        see {@link com.travelplanner.domain.port.KnowledgePort}
 * @param departureCity free text. Not a slug: people depart from places the knowledge base has
 *        never curated, and forcing coverage on the origin would refuse a valid trip
 * @param budget the ceiling for the whole trip, not a per-day figure
 * @param interests the C2 ranking signal (PLAN §4.1.2). Duplicates are collapsed; order is not
 *        meaningful
 */
public record TripBriefDetails(
        List<String> destinations,
        DateRange dates,
        DateFlexibility dateFlexibility,
        String departureCity,
        Money budget,
        PartySize party,
        List<TravelInterest> interests,
        TravelPace pace) {

    /** Matches {@code trip_brief.departure_city varchar(120)}. */
    public static final int MAX_DEPARTURE_CITY_LENGTH = 120;

    /** More candidate destinations than this is a list nobody ranked, not a preference. */
    public static final int MAX_DESTINATIONS = 10;

    public TripBriefDetails {
        destinations = normalisedDestinations(destinations);
        interests = normalisedInterests(interests);
        departureCity = normalisedDepartureCity(departureCity);
    }

    /** The starting point for a brand-new brief: nothing decided, nothing invalid. */
    public static TripBriefDetails empty() {
        return new TripBriefDetails(List.of(), null, null, null, null, null, List.of(), null);
    }

    public TripBriefDetails withDestinations(List<String> newDestinations) {
        return new TripBriefDetails(newDestinations, dates, dateFlexibility, departureCity,
                budget, party, interests, pace);
    }

    public TripBriefDetails withDates(DateRange newDates) {
        return new TripBriefDetails(destinations, newDates, dateFlexibility, departureCity,
                budget, party, interests, pace);
    }

    public TripBriefDetails withDateFlexibility(DateFlexibility newFlexibility) {
        return new TripBriefDetails(destinations, dates, newFlexibility, departureCity,
                budget, party, interests, pace);
    }

    public TripBriefDetails withDepartureCity(String newDepartureCity) {
        return new TripBriefDetails(destinations, dates, dateFlexibility, newDepartureCity,
                budget, party, interests, pace);
    }

    public TripBriefDetails withBudget(Money newBudget) {
        return new TripBriefDetails(destinations, dates, dateFlexibility, departureCity,
                newBudget, party, interests, pace);
    }

    public TripBriefDetails withParty(PartySize newParty) {
        return new TripBriefDetails(destinations, dates, dateFlexibility, departureCity,
                budget, newParty, interests, pace);
    }

    public TripBriefDetails withInterests(List<TravelInterest> newInterests) {
        return new TripBriefDetails(destinations, dates, dateFlexibility, departureCity,
                budget, party, newInterests, pace);
    }

    public TripBriefDetails withPace(TravelPace newPace) {
        return new TripBriefDetails(destinations, dates, dateFlexibility, departureCity,
                budget, party, interests, newPace);
    }

    /**
     * Slugs are trimmed, lower-cased, and de-duplicated so that {@code ["Kyoto", "kyoto "]} is one
     * preference rather than two. Blank entries are dropped rather than rejected: a form that
     * submits an empty row is a UI artefact, not a user's statement about anything.
     */
    private static List<String> normalisedDestinations(List<String> destinations) {
        if (destinations == null) {
            return List.of();
        }
        List<String> cleaned = destinations.stream()
                .filter(Objects::nonNull)
                .map(slug -> slug.trim().toLowerCase(Locale.ROOT))
                .filter(slug -> !slug.isEmpty())
                .distinct()
                .toList();
        if (cleaned.size() > MAX_DESTINATIONS) {
            throw ValidationFailedException.field("destinations",
                    "must name at most " + MAX_DESTINATIONS + " destinations");
        }
        return cleaned;
    }

    private static List<TravelInterest> normalisedInterests(List<TravelInterest> interests) {
        if (interests == null) {
            return List.of();
        }
        return interests.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static String normalisedDepartureCity(String departureCity) {
        if (departureCity == null) {
            return null;
        }
        String trimmed = departureCity.trim();
        if (trimmed.isEmpty()) {
            // Blank is "not answered", not "answered with nothing" — collapsing the two here keeps
            // the clarification rule below from having to know about whitespace.
            return null;
        }
        if (trimmed.length() > MAX_DEPARTURE_CITY_LENGTH) {
            throw ValidationFailedException.field("departure_city",
                    "must be at most " + MAX_DEPARTURE_CITY_LENGTH + " characters");
        }
        return trimmed;
    }
}
