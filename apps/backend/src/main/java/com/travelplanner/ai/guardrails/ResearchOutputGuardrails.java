package com.travelplanner.ai.guardrails;

import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.DestinationNarrative;
import com.travelplanner.domain.model.TravelerGuide;
import com.travelplanner.domain.valueobject.RecommendationSourceRef;
import com.travelplanner.ai.tool.ResearchEvidenceLedger;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rejects invented destinations, POIs, apps, and source_refs on research narratives (UC-K06).
 *
 * <p>Numeric fit scores are never accepted from the model — they are not present on
 * {@link DestinationNarrative} at all. This class only checks that every citation and named
 * entity the narrative claims was retrieved through the tool ledger.
 */
public final class ResearchOutputGuardrails {

    private ResearchOutputGuardrails() {
    }

    public static void validate(
            List<DestinationNarrative> narratives,
            Set<UUID> allowedDestinationIds,
            ResearchEvidenceLedger ledger) {
        Objects.requireNonNull(narratives, "narratives");
        Objects.requireNonNull(allowedDestinationIds, "allowedDestinationIds");
        Objects.requireNonNull(ledger, "ledger");
        for (DestinationNarrative narrative : narratives) {
            validateOne(narrative, allowedDestinationIds, ledger);
        }
    }

    private static void validateOne(
            DestinationNarrative narrative,
            Set<UUID> allowedDestinationIds,
            ResearchEvidenceLedger ledger) {
        if (!allowedDestinationIds.contains(narrative.destinationId())) {
            throw ValidationFailedException.field("destination_id",
                    "invented destination id " + narrative.destinationId());
        }
        if (!ledger.allowsDestination(narrative.slug())) {
            throw ValidationFailedException.field("destination_slug",
                    "invented destination slug " + narrative.slug());
        }
        for (RecommendationSourceRef ref : narrative.sourceRefs()) {
            requireKnownSource(ref, ledger);
        }
        TravelerGuide guide = narrative.travelerGuide();
        for (RecommendationSourceRef ref : guide.sourceRefs()) {
            requireKnownSource(ref, ledger);
        }
        for (TravelerGuide.LocalAppPackEntry app : guide.localAppPack()) {
            app.slugIfPresent().ifPresent(slug -> {
                if (!ledger.allowsApp(slug)) {
                    throw ValidationFailedException.field("local_app_pack",
                            "invented travel app slug " + slug);
                }
            });
        }
        rejectUnknownQuotedPois(guide.highlights(), ledger);
    }

    private static void requireKnownSource(
            RecommendationSourceRef ref, ResearchEvidenceLedger ledger) {
        if (!ledger.allowsSourceRef(ref.sourceRef())) {
            throw ValidationFailedException.field("source_refs",
                    "invented source_ref " + ref.sourceRef());
        }
    }

    /**
     * Highlights that look like {@code slug: Name} must use a retrieved POI slug. Free prose without
     * a slug prefix is allowed (it is narrative), but a slug prefix is a factual claim.
     */
    private static void rejectUnknownQuotedPois(List<String> highlights, ResearchEvidenceLedger ledger) {
        Set<String> known = ledger.sourceRefs().stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (String highlight : highlights) {
            int colon = highlight.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String maybeSlug = highlight.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            if (maybeSlug.matches("[a-z0-9-]{2,80}") && !ledger.allowsPoi(maybeSlug)
                    && !known.contains(maybeSlug)) {
                throw ValidationFailedException.field("highlights",
                        "invented POI slug " + maybeSlug);
            }
        }
    }
}
