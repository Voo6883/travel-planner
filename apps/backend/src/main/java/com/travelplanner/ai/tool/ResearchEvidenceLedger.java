package com.travelplanner.ai.tool;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Collects every {@code source_ref} (and POI/app slug) a tool returned during one research run.
 *
 * <p>Guardrails refuse any citation or named entity that is not in this ledger — that is how
 * invented destinations / POIs / apps / source_refs fail closed (UC-K06).
 */
public final class ResearchEvidenceLedger {

    private final Set<String> sourceRefs = new LinkedHashSet<>();
    private final Set<String> poiSlugs = new LinkedHashSet<>();
    private final Set<String> appSlugs = new LinkedHashSet<>();
    private final Set<String> areaNames = new LinkedHashSet<>();
    private final Set<String> destinationSlugs = new LinkedHashSet<>();

    public void allowDestination(String slug) {
        if (slug != null && !slug.isBlank()) {
            destinationSlugs.add(slug.trim().toLowerCase());
        }
    }

    public void noteSourceRef(String sourceRef) {
        if (sourceRef != null && !sourceRef.isBlank()) {
            sourceRefs.add(sourceRef.trim());
        }
    }

    public void notePoiSlug(String slug) {
        if (slug != null && !slug.isBlank()) {
            poiSlugs.add(slug.trim().toLowerCase());
        }
    }

    public void noteAppSlug(String slug) {
        if (slug != null && !slug.isBlank()) {
            appSlugs.add(slug.trim().toLowerCase());
        }
    }

    public void noteAreaName(String name) {
        if (name != null && !name.isBlank()) {
            areaNames.add(name.trim().toLowerCase());
        }
    }

    public boolean allowsSourceRef(String sourceRef) {
        return sourceRef != null && sourceRefs.contains(sourceRef.trim());
    }

    public boolean allowsDestination(String slug) {
        return slug != null && destinationSlugs.contains(slug.trim().toLowerCase());
    }

    public boolean allowsPoi(String slug) {
        return slug != null && poiSlugs.contains(slug.trim().toLowerCase());
    }

    public boolean allowsApp(String slug) {
        return slug != null && appSlugs.contains(slug.trim().toLowerCase());
    }

    public boolean allowsArea(String name) {
        return name != null && areaNames.contains(name.trim().toLowerCase());
    }

    public Set<String> sourceRefs() {
        return Collections.unmodifiableSet(sourceRefs);
    }

    public void mergeToolJson(String toolName, String resultJson) {
        Objects.requireNonNull(toolName, "toolName");
        if (resultJson == null || resultJson.isBlank()) {
            return;
        }
        // Lightweight scrape: every "source_ref":"…" and known slug fields.
        int index = 0;
        while ((index = resultJson.indexOf("\"source_ref\"", index)) >= 0) {
            String value = readJsonStringAfter(resultJson, index + "\"source_ref\"".length());
            noteSourceRef(value);
            index += 12;
        }
        if (KnowledgeResearchTools.GET_FOOD_POIS.equals(toolName)
                || KnowledgeResearchTools.GET_POIS.equals(toolName)) {
            scrapeSlugs(resultJson, poiSlugs);
        }
        if (KnowledgeResearchTools.GET_TRAVEL_APPS.equals(toolName)) {
            scrapeSlugs(resultJson, appSlugs);
        }
        if (KnowledgeResearchTools.GET_AREAS.equals(toolName)) {
            scrapeNames(resultJson, areaNames);
        }
    }

    private static void scrapeSlugs(String json, Set<String> into) {
        int index = 0;
        while ((index = json.indexOf("\"slug\"", index)) >= 0) {
            String value = readJsonStringAfter(json, index + "\"slug\"".length());
            if (value != null && !value.isBlank()) {
                into.add(value.trim().toLowerCase());
            }
            index += 6;
        }
    }

    private static void scrapeNames(String json, Set<String> into) {
        int index = 0;
        while ((index = json.indexOf("\"name\"", index)) >= 0) {
            String value = readJsonStringAfter(json, index + "\"name\"".length());
            if (value != null && !value.isBlank()) {
                into.add(value.trim().toLowerCase());
            }
            index += 6;
        }
    }

    private static String readJsonStringAfter(String json, int from) {
        int colon = json.indexOf(':', from);
        if (colon < 0) {
            return null;
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        int end = start + 1;
        while (end < json.length()) {
            char ch = json.charAt(end);
            if (ch == '\\') {
                end += 2;
                continue;
            }
            if (ch == '"') {
                return json.substring(start + 1, end);
            }
            end++;
        }
        return null;
    }
}
