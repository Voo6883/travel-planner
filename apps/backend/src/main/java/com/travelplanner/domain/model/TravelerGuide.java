package com.travelplanner.domain.model;

import com.travelplanner.domain.valueobject.RecommendationSourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-recommendation traveler guide sections (PLAN §4.1 {@code traveler_guide}).
 *
 * <p>Narrative text may be LLM-authored, but every factual claim must already be present in the
 * KnowledgePort tool results that grounded this run — {@link #sourceRefs()} carries those
 * citations. An empty overview is refused: a guide with nothing to say is not a guide.
 */
public record TravelerGuide(
        String overview,
        String whyNow,
        List<String> areas,
        String food,
        List<String> highlights,
        String mobility,
        String practical,
        List<LocalAppPackEntry> localAppPack,
        List<RecommendationSourceRef> sourceRefs) {

    public TravelerGuide {
        Objects.requireNonNull(overview, "overview");
        if (overview.isBlank()) {
            throw new IllegalArgumentException("overview must not be blank");
        }
        whyNow = blankToNull(whyNow);
        food = blankToNull(food);
        mobility = blankToNull(mobility);
        practical = blankToNull(practical);
        areas = copyNonBlank(areas);
        highlights = copyNonBlank(highlights);
        localAppPack = localAppPack == null ? List.of() : List.copyOf(localAppPack);
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
    }

    public Optional<String> whyNowIfPresent() {
        return Optional.ofNullable(whyNow);
    }

    public Optional<String> foodIfPresent() {
        return Optional.ofNullable(food);
    }

    public Optional<String> mobilityIfPresent() {
        return Optional.ofNullable(mobility);
    }

    public Optional<String> practicalIfPresent() {
        return Optional.ofNullable(practical);
    }

    /**
     * One entry in {@code traveler_guide.local_app_pack} (PLAN §4.1.2).
     *
     * @param usage category label such as {@code ride_hail} or {@code maps}
     * @param name display name (may include a local-script name)
     * @param slug travel_app slug when known
     */
    public record LocalAppPackEntry(String usage, String name, String slug) {

        public LocalAppPackEntry {
            Objects.requireNonNull(usage, "usage");
            Objects.requireNonNull(name, "name");
            if (usage.isBlank() || name.isBlank()) {
                throw new IllegalArgumentException("usage and name must not be blank");
            }
            slug = blankToNull(slug);
        }

        public Optional<String> slugIfPresent() {
            return Optional.ofNullable(slug);
        }
    }

    private static List<String> copyNonBlank(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .toList();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
