package com.travelplanner.infrastructure.knowledge;

import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.domain.model.DestinationGuide;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.PriceObservation;
import com.travelplanner.domain.model.RouteSegment;
import com.travelplanner.domain.model.SeasonalityMonth;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.domain.model.TravelAppReplacement;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.AppReplacementNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.AreaNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.PoiNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.PriceObservationNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.RouteSegmentNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.SeasonalityNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.TransportModeNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.TravelAppNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates a seed file by <strong>building the domain objects it will become</strong>
 * (gate 17B's seed validator; closes <b>F-44</b>).
 *
 * <h2>Why construction rather than a list of rules</h2>
 *
 * <p>{@code SampleKnowledgeReader} already checks the rules ADR 010 §3 states about the seed itself:
 * every row cites the reserved source ref, the source is {@code SAMPLE_DATA}/{@code SAMPLE}, no file
 * claims {@code FULL} coverage. What it could not check is everything the <em>domain</em> knows, because
 * it never built a domain object — so every invariant living in a record's constructor was unenforced
 * until a row was read back out of the database, long after the seed had loaded green.
 *
 * <p>F-44 is the instance that surfaced it. {@code price_history.amount} is one {@code numeric(12,2)}
 * column for every currency while {@link Money} enforces the currency's own minor units, and JPY has
 * none — so {@code 4000.10 JPY} inserted cleanly and then threw on <em>every</em> read of that
 * destination's price history. Tokyo is the product's first destination, so this was not hypothetical.
 *
 * <p>The general fix is to stop enumerating rules. Constructing {@link PriceObservation} runs
 * {@code Money}'s scale check; constructing {@link Destination} runs the IANA zone check and the
 * coordinate ranges; {@link Poi} runs its slug and tag rules; {@link TravelAppReplacement} runs the
 * slug-shape rule that decides whether a suppression matches anything at all. Every invariant added to
 * a record in future is enforced here the day it is written, with nothing to remember. A hand-copied
 * list of checks would have drifted from the records by the second one added.
 *
 * <h2>Placeholder identity, real provenance</h2>
 *
 * <p>Ids are generated at write time, so the constructors are handed one placeholder UUID. That is
 * sound because no record validates an id beyond non-nullity — and if one ever does, this class is
 * where it will be noticed rather than a place it can hide.
 *
 * <p>Provenance is <em>real</em>: built from the parsed {@code sources.json}, so
 * {@link KnowledgeProvenance}'s own rules — a licence requiring attribution must have attribution text,
 * the reserved sample ref must have no URL — are exercised against the file rather than a fixture.
 *
 * <h2>Every problem, not the first</h2>
 *
 * <p>Failures are collected and reported together. A validator that stops at the first bad row makes
 * fixing a seed an N-round loop, and a curator working through twenty prices deserves to see twenty
 * messages once. Each carries the file, the node, and the offending value.
 */
final class SampleSeedDomainCheck {

    /** Stands in for the ids the writer generates. No record validates an id beyond non-nullity. */
    private static final UUID PLACEHOLDER = new UUID(0L, 0L);

    private SampleSeedDomainCheck() {
    }

    /**
     * @param provenance built from {@code sources.json}, so the seed's own citation is validated too
     * @throws IllegalStateException listing every domain invariant the file violates
     */
    static void requireDomainValid(SampleKnowledgeDocument document, String file,
            KnowledgeProvenance provenance) {
        List<String> problems = new ArrayList<>();

        checkDestination(document, problems);
        checkGuide(document, provenance, problems);
        checkAreas(document, provenance, problems);
        checkPois(document, provenance, problems);
        checkTransportModes(document, provenance, problems);
        checkRouteSegments(document, provenance, problems);
        checkSeasonality(document, provenance, problems);
        checkPrices(document, provenance, problems);
        checkTravelApps(document, provenance, problems);

        if (!problems.isEmpty()) {
            throw new IllegalStateException(file + " does not satisfy the domain's own invariants. "
                    + "These would otherwise surface when a row is READ, long after the seed loaded:\n  - "
                    + String.join("\n  - ", problems));
        }
    }

    private static void checkDestination(SampleKnowledgeDocument document, List<String> problems) {
        var node = document.destination();
        // Destination validates the IANA zone, both coordinate ranges, the country code width, and a
        // non-blank name. `Asia/Tokio` would otherwise reach task 28's scheduler as curated data.
        attempt(problems, "destination " + node.slug(), () -> new Destination(PLACEHOLDER, node.slug(),
                node.name(), node.countryCode(), node.timezone(), asDouble(node.latitude()), asDouble(node.longitude()),
                node.coverageLevel()));
    }

    private static void checkGuide(SampleKnowledgeDocument document, KnowledgeProvenance provenance,
            List<String> problems) {
        var node = document.guide();
        attempt(problems, "guide", () -> new DestinationGuide(PLACEHOLDER, PLACEHOLDER, node.locale(),
                node.overview(), node.food(), node.practical(), provenance, 0));
    }

    private static void checkAreas(SampleKnowledgeDocument document, KnowledgeProvenance provenance,
            List<String> problems) {
        Set<String> slugs = new HashSet<>();
        for (AreaNode node : document.areas()) {
            attempt(problems, "area " + node.slug(), () -> new DestinationArea(PLACEHOLDER, PLACEHOLDER,
                    node.slug(), node.name(), node.description(), asDouble(node.latitude()),
                    asDouble(node.longitude()), provenance));
            // uq_destination_area_destination_slug. A duplicate is a constraint violation part-way
            // through a seed, which leaves the database half-populated and names a table rather than a file.
            if (!slugs.add(node.slug())) {
                problems.add("area slug '" + node.slug() + "' appears twice; it is unique per destination");
            }
        }
    }

    private static void checkPois(SampleKnowledgeDocument document, KnowledgeProvenance provenance,
            List<String> problems) {
        Set<String> areaSlugs = document.areas().stream().map(AreaNode::slug).collect(java.util.stream
                .Collectors.toSet());
        Set<String> slugs = new HashSet<>();
        for (PoiNode node : document.pois()) {
            attempt(problems, "poi " + node.slug(), () -> new Poi(PLACEHOLDER, PLACEHOLDER, null,
                    node.slug(), node.name(), node.description(), node.category(), node.tags(),
                    node.locale(), asDouble(node.latitude()), asDouble(node.longitude()), node.openingHours(),
                    node.priceBand(), provenance, 0));
            if (!slugs.add(node.slug())) {
                problems.add("poi slug '" + node.slug() + "' appears twice; it is unique per destination");
            }
            // The writer resolves this to an area id; an unknown slug silently becomes a POI attached to
            // nothing, which C3 then cannot group geographically.
            if (node.areaSlug() != null && !areaSlugs.contains(node.areaSlug())) {
                problems.add("poi '" + node.slug() + "' names area '" + node.areaSlug()
                        + "', which this file does not define");
            }
        }
    }

    private static void checkTransportModes(SampleKnowledgeDocument document,
            KnowledgeProvenance provenance, List<String> problems) {
        for (TransportModeNode node : document.transportModes()) {
            attempt(problems, "transport mode " + node.slug(), () -> new TransportMode(PLACEHOLDER,
                    PLACEHOLDER, node.slug(), node.name(), node.kind(), node.description(),
                    node.costBand(), node.touristFriendly(), provenance));
        }
    }

    private static void checkRouteSegments(SampleKnowledgeDocument document,
            KnowledgeProvenance provenance, List<String> problems) {
        Set<String> areaSlugs = document.areas().stream().map(AreaNode::slug).collect(java.util.stream
                .Collectors.toSet());
        Set<String> modeSlugs = document.transportModes().stream().map(TransportModeNode::slug)
                .collect(java.util.stream.Collectors.toSet());

        for (RouteSegmentNode node : document.routeSegments()) {
            String where = "route segment " + node.fromAreaSlug() + " -> " + node.toAreaSlug();
            // RouteSegment refuses from == to and a non-positive duration, and rejects a sub-minute
            // Duration rather than truncating it — duration_minutes is an integer column.
            attempt(problems, where, () -> new RouteSegment(PLACEHOLDER, PLACEHOLDER, PLACEHOLDER,
                    new UUID(0L, 1L), PLACEHOLDER, Duration.ofMinutes(node.durationMinutes()),
                    node.estimated(), node.notes(), provenance));
            for (String slug : List.of(node.fromAreaSlug(), node.toAreaSlug())) {
                if (!areaSlugs.contains(slug)) {
                    problems.add(where + " names area '" + slug + "', which this file does not define");
                }
            }
            if (!modeSlugs.contains(node.transportModeSlug())) {
                problems.add(where + " names transport mode '" + node.transportModeSlug()
                        + "', which this file does not define");
            }
            if (node.fromAreaSlug().equals(node.toAreaSlug())) {
                problems.add(where + " starts and ends in the same area");
            }
        }
    }

    private static void checkSeasonality(SampleKnowledgeDocument document,
            KnowledgeProvenance provenance, List<String> problems) {
        Set<Integer> months = new HashSet<>();
        for (SeasonalityNode node : document.seasonality()) {
            attempt(problems, "seasonality month " + node.month(), () -> new SeasonalityMonth(PLACEHOLDER,
                    PLACEHOLDER, node.month(), node.weatherBand(), node.crowdBand(), node.priceBand(),
                    node.notes(), provenance));
            if (!months.add(node.month())) {
                problems.add("seasonality month " + node.month()
                        + " appears twice; uq_seasonality_destination_month permits one row per month");
            }
        }
        // Not an error for sample data — ADR 010 §1 requires all twelve only for FULL coverage, and a
        // sample file may not claim FULL. Stated so a curator promoting a file to FULL knows the bar.
        if (!months.isEmpty() && months.size() != 12) {
            problems.add("seasonality has " + months.size() + " of 12 months; a destination cannot reach "
                    + "FULL coverage without all twelve (ADR 010 §1)");
        }
    }

    /**
     * The check F-44 exists for.
     *
     * <p>{@link Money} enforces the currency's own minor units. JPY has none, so {@code 4000.10} is not a
     * yen amount at all — and the column, being {@code numeric(12,2)} for every currency, accepts it
     * happily. Building the {@link PriceObservation} here is what turns that from a read-time failure
     * into a seed-time one, with the file and the category named.
     */
    private static void checkPrices(SampleKnowledgeDocument document, KnowledgeProvenance provenance,
            List<String> problems) {
        for (PriceObservationNode node : document.priceHistory()) {
            attempt(problems, "price " + node.category() + " (" + node.amount() + " " + node.currency()
                    + ", observed " + node.observedOn() + ")",
                    () -> new PriceObservation(PLACEHOLDER, PLACEHOLDER, node.category(),
                            Money.of(String.valueOf(node.amount()), node.currency()), node.observedOn(), provenance));
            // ck_price_history_observed_on_first_of_month. A mid-month date is a monthly series that
            // silently means something else.
            if (node.observedOn() != null && node.observedOn().getDayOfMonth() != 1) {
                problems.add("price " + node.category() + " is observed on " + node.observedOn()
                        + "; price_history holds monthly figures dated the first of the month");
            }
        }
    }

    private static void checkTravelApps(SampleKnowledgeDocument document,
            KnowledgeProvenance provenance, List<String> problems) {
        for (TravelAppNode node : document.travelApps()) {
            // TravelApp refuses an entry with neither store link — the whole point of an app pack is
            // "install this before you fly", and an unlinked row is not actionable.
            attempt(problems, "travel app " + node.slug(), () -> new TravelApp(PLACEHOLDER,
                    node.countryCode(), node.slug(), node.name(), node.category(), node.description(),
                    node.iosUrl(), node.androidUrl(), provenance));

            Set<String> replacedKeys = new HashSet<>();
            for (AppReplacementNode replacement : node.replaces()) {
                String where = "travel app " + node.slug() + " replaces '"
                        + replacement.replacedAppKey() + "'";
                // TravelAppReplacement validates the slug shape, which is what decides whether the
                // suppression matches anything. A trailing space suppresses nothing, silently.
                attempt(problems, where, () -> new TravelAppReplacement(PLACEHOLDER, PLACEHOLDER,
                        replacement.replacedAppKey(), replacement.replacedAppName(),
                        replacement.reason(), replacement.detail(), provenance));
                if (!replacedKeys.add(replacement.replacedAppKey())) {
                    problems.add(where + " twice; uq_travel_app_replacement_pair permits one statement "
                            + "per (local app, replaced app) pair");
                }
            }
        }
    }

    /**
     * {@code numeric(9,6)} arrives as {@link java.math.BigDecimal}; the domain models a coordinate as a
     * {@code Double}. Converted here rather than in each caller so the null case — an uncurated
     * coordinate, which is legitimate — is handled once.
     */
    private static Double asDouble(java.math.BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    /**
     * Runs one construction, turning any rejection into a collected problem.
     *
     * <p>Catches {@link RuntimeException} rather than the two specific types the records throw
     * ({@code IllegalArgumentException}, {@code ValidationFailedException}) — plus
     * {@link NullPointerException} from a missing required field. Narrowing it would mean a new
     * validation exception type escaping this loop and aborting on the first bad row, which is the
     * behaviour this class exists to avoid.
     */
    private static void attempt(List<String> problems, String where, Runnable construction) {
        try {
            construction.run();
        } catch (RuntimeException rejected) {
            problems.add(where + ": " + reasonOf(rejected));
        }
    }

    /**
     * The rejection's actual reason, which for one exception type is not its message.
     *
     * <p>{@link ValidationFailedException#getMessage()} is "The request is not valid." — deliberately, it
     * is what an HTTP client sees, and the constraint detail lives in {@code details().fields}. That is
     * right for a 400 response and useless in a seed report: F-44's own failure came out as
     * {@code price HOTEL_NIGHT (4000.10 JPY): The request is not valid.}, naming the row and hiding the
     * one fact a curator needs. Unwrapping the field map turns it into "has more decimal places than JPY
     * allows".
     */
    @SuppressWarnings("unchecked")
    private static String reasonOf(RuntimeException rejected) {
        if (rejected instanceof ValidationFailedException validation) {
            Object fields = validation.details().get("fields");
            if (fields instanceof Map<?, ?> byField && !byField.isEmpty()) {
                return ((Map<String, String>) byField).entrySet().stream()
                        .map(field -> field.getKey() + " " + field.getValue())
                        .collect(java.util.stream.Collectors.joining("; "));
            }
        }
        return rejected.getMessage() == null
                ? rejected.getClass().getSimpleName()
                : rejected.getMessage();
    }
}
