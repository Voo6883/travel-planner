package com.travelplanner.infrastructure.knowledge;

import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.valueobject.KnowledgeQuery;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.AreaNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.PoiNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.RouteSegmentNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.SeasonalityNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.TravelAppNode;
import com.travelplanner.infrastructure.knowledge.SampleSourceDocument.SourceNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural quality checks over classpath seed files (task 17, ADR 010 Consequences / PM-K03).
 *
 * <p>Runs without a database. Detects duplicate slugs/names, orphan {@code source_ref}s, invalid
 * coordinates, missing provenance, incompatible embedding dimension assumptions, and locale-app
 * country mismatches. ADR 010 §1 depth floors apply only when a destination declares
 * {@code FULL} — sample files stay {@code PARTIAL} by design (F-34).
 */
public final class SampleKnowledgeValidator {

    private static final Instant SEEDED_AT = Instant.parse("2026-07-29T00:00:00Z");

    private final SampleKnowledgeReader reader = new SampleKnowledgeReader();

    /** Validates every destination in {@link SampleKnowledgeReader#DESTINATION_SLUGS}. */
    public List<String> validateAll() {
        List<String> errors = new ArrayList<>();
        SourceNode source;
        try {
            source = reader.readSampleSource(SEEDED_AT);
        } catch (RuntimeException failure) {
            errors.add("sources.json: " + failure.getMessage());
            return List.copyOf(errors);
        }
        Set<String> knownRefs = Set.of(source.sourceRef());
        if (KnowledgeQuery.EMBEDDING_DIMENSION != 1536) {
            errors.add("embedding dimension pin drifted from ADR 010 §5 (expected 1536)");
        }
        for (String slug : SampleKnowledgeReader.DESTINATION_SLUGS) {
            errors.addAll(validateDestination(slug, knownRefs));
        }
        return List.copyOf(errors);
    }

    private List<String> validateDestination(String slug, Set<String> knownRefs) {
        List<String> errors = new ArrayList<>();
        SampleKnowledgeDocument document;
        try {
            document = reader.readDestination(slug);
        } catch (RuntimeException failure) {
            errors.add(slug + ": " + failure.getMessage());
            return errors;
        }
        String file = slug + ".json";
        checkDuplicates(document, file, errors);
        checkOrphans(new OrphanCheck(document, file, knownRefs, errors));
        checkCoordinates(document, file, errors);
        checkSeasonality(document, file, errors);
        checkRoutes(document, file, errors);
        checkApps(document, file, errors);
        checkFullDepth(document, file, errors);
        return errors;
    }

    private static void checkDuplicates(
            SampleKnowledgeDocument document, String file, List<String> errors) {
        Set<String> areaSlugs = new HashSet<>();
        Set<String> areaNames = new HashSet<>();
        for (AreaNode area : document.areas()) {
            if (!areaSlugs.add(area.slug())) {
                errors.add(file + ": duplicate area slug '" + area.slug() + "'");
            }
            if (!areaNames.add(area.name())) {
                errors.add(file + ": duplicate area name '" + area.name() + "'");
            }
        }
        Set<String> poiSlugs = new HashSet<>();
        Set<String> poiNames = new HashSet<>();
        for (PoiNode poi : document.pois()) {
            if (!poiSlugs.add(poi.slug())) {
                errors.add(file + ": duplicate poi slug '" + poi.slug() + "'");
            }
            if (!poiNames.add(poi.name())) {
                errors.add(file + ": duplicate poi name '" + poi.name() + "'");
            }
        }
    }

    private static void checkOrphans(OrphanCheck check) {
        requireKnown(check, check.document.guide().sourceRef(), "guide");
        for (AreaNode area : check.document.areas()) {
            requireKnown(check, area.sourceRef(), "area " + area.slug());
        }
        for (PoiNode poi : check.document.pois()) {
            requireKnown(check, poi.sourceRef(), "poi " + poi.slug());
            if (poi.sourceRef() == null || poi.sourceRef().isBlank()) {
                check.errors.add(check.file + ": poi " + poi.slug() + " missing provenance");
            }
        }
        for (TravelAppNode app : check.document.travelApps()) {
            requireKnown(check, app.sourceRef(), "app " + app.slug());
        }
    }

    private static void requireKnown(OrphanCheck check, String sourceRef, String where) {
        if (!check.knownRefs.contains(sourceRef)) {
            check.errors.add(check.file + ": orphan source_ref '" + sourceRef + "' on " + where);
        }
    }

    private static void checkCoordinates(
            SampleKnowledgeDocument document, String file, List<String> errors) {
        checkPair(document.destination().latitude(), document.destination().longitude(),
                file + " destination", errors);
        for (AreaNode area : document.areas()) {
            checkPair(area.latitude(), area.longitude(), file + " area " + area.slug(), errors);
        }
        for (PoiNode poi : document.pois()) {
            checkPair(poi.latitude(), poi.longitude(), file + " poi " + poi.slug(), errors);
        }
    }

    private static void checkPair(
            BigDecimal lat, BigDecimal lon, String where, List<String> errors) {
        if ((lat == null) != (lon == null)) {
            errors.add(where + " has unpaired coordinates");
            return;
        }
        if (lat == null) {
            return;
        }
        if (lat.doubleValue() < -90 || lat.doubleValue() > 90) {
            errors.add(where + " latitude out of range");
        }
        if (lon.doubleValue() < -180 || lon.doubleValue() > 180) {
            errors.add(where + " longitude out of range");
        }
    }

    private static void checkSeasonality(
            SampleKnowledgeDocument document, String file, List<String> errors) {
        Set<Integer> months = new HashSet<>();
        for (SeasonalityNode month : document.seasonality()) {
            if (!months.add(month.month())) {
                errors.add(file + ": duplicate seasonality month " + month.month());
            }
            if (month.month() < 1 || month.month() > 12) {
                errors.add(file + ": seasonality month out of range: " + month.month());
            }
        }
    }

    private static void checkRoutes(
            SampleKnowledgeDocument document, String file, List<String> errors) {
        Set<String> areas = new HashSet<>();
        for (AreaNode area : document.areas()) {
            areas.add(area.slug());
        }
        Set<String> modes = new HashSet<>();
        document.transportModes().forEach(mode -> modes.add(mode.slug()));
        for (RouteSegmentNode segment : document.routeSegments()) {
            if (!areas.contains(segment.fromAreaSlug()) || !areas.contains(segment.toAreaSlug())) {
                errors.add(file + ": route references unknown area "
                        + segment.fromAreaSlug() + " -> " + segment.toAreaSlug());
            }
            if (!modes.contains(segment.transportModeSlug())) {
                errors.add(file + ": route references unknown transport mode "
                        + segment.transportModeSlug());
            }
        }
        for (PoiNode poi : document.pois()) {
            if (poi.areaSlug() != null && !areas.contains(poi.areaSlug())) {
                errors.add(file + ": poi " + poi.slug() + " references unknown area "
                        + poi.areaSlug());
            }
        }
    }

    private static void checkApps(
            SampleKnowledgeDocument document, String file, List<String> errors) {
        String country = document.destination().countryCode();
        Set<String> slugs = new HashSet<>();
        for (TravelAppNode app : document.travelApps()) {
            if (!country.equals(app.countryCode())) {
                errors.add(file + ": travel app " + app.slug() + " country_code "
                        + app.countryCode() + " does not match destination " + country);
            }
            if (!slugs.add(app.slug())) {
                errors.add(file + ": duplicate travel app slug '" + app.slug() + "'");
            }
            if ((app.iosUrl() == null || app.iosUrl().isBlank())
                    && (app.androidUrl() == null || app.androidUrl().isBlank())) {
                errors.add(file + ": travel app " + app.slug() + " has no store link");
            }
        }
    }

    /**
     * ADR 010 §1 depth floors — only enforced for {@code FULL} destinations. Sample files are
     * deliberately {@code PARTIAL} and below the floor (F-34).
     */
    private static void checkFullDepth(
            SampleKnowledgeDocument document, String file, List<String> errors) {
        if (document.destination().coverageLevel() != CoverageLevel.FULL) {
            return;
        }
        if (document.areas().size() < 4) {
            errors.add(file + ": FULL destination needs ≥4 areas, has " + document.areas().size());
        }
        if (document.pois().size() < 25) {
            errors.add(file + ": FULL destination needs ≥25 pois, has " + document.pois().size());
        }
        long food = document.pois().stream().filter(poi -> poi.category() == PoiCategory.FOOD).count();
        if (food < 8) {
            errors.add(file + ": FULL destination needs ≥8 FOOD pois, has " + food);
        }
        if (document.travelApps().size() < 3) {
            errors.add(file + ": FULL destination needs ≥3 travel apps, has "
                    + document.travelApps().size());
        }
        if (document.seasonality().size() != 12) {
            errors.add(file + ": FULL destination needs 12 seasonality months, has "
                    + document.seasonality().size());
        }
    }

    private record OrphanCheck(
            SampleKnowledgeDocument document,
            String file,
            Set<String> knownRefs,
            List<String> errors) {
    }
}
