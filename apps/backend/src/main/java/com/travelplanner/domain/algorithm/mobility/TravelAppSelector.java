package com.travelplanner.domain.algorithm.mobility;

import com.travelplanner.domain.enums.TransportKind;
import com.travelplanner.domain.enums.TravelAppCategory;
import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.domain.model.TravelAppReplacement;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Which apps to put on a leg (UC-C3-11, UC-K11, UC-K14).
 *
 * <h2>Suppression is the point</h2>
 *
 * <p>UC-K14 is not a nicety. Recommending Uber for a ride in Shanghai is not a slightly worse
 * suggestion than 滴滴 — it is an app the traveller cannot use, offered at the moment they are
 * standing on a kerb. The knowledge base records that relationship explicitly in
 * {@link TravelAppReplacement}, and this is the one place it is applied, so no call site can forget.
 *
 * <p><strong>Suppression is by key, and it wins over category matching.</strong> An app is dropped
 * because the corpus says it is replaced in this market, regardless of how well it otherwise fits
 * the leg. The inverse — a local app with no global counterpart — needs no rule at all: it is simply
 * present.
 */
public final class TravelAppSelector {

    private TravelAppSelector() {
    }

    /**
     * The apps worth showing for one leg, in a stable order.
     *
     * @param apps the destination country's pack, from {@code KnowledgePort.findTravelApps}
     * @param replacements from {@code KnowledgePort.findTravelAppReplacements}; each names a global
     *        app that must not be offered here
     * @return apps whose category serves this mode, minus everything suppressed. Empty is a
     *         legitimate answer — a walk needs no app, and so does a market nobody has curated
     */
    public static List<TravelApp> forMode(
            TransportKind mode, List<TravelApp> apps, List<TravelAppReplacement> replacements) {
        Objects.requireNonNull(apps, "apps");
        Objects.requireNonNull(replacements, "replacements");
        if (mode == null) {
            // An UNKNOWN leg recommends nothing. Suggesting a transit app for a journey we could not
            // describe would imply we knew how it was made.
            return List.of();
        }

        Set<TravelAppCategory> wanted = categoriesFor(mode);
        if (wanted.isEmpty()) {
            return List.of();
        }
        return apps.stream()
                .filter(app -> wanted.contains(app.category()))
                .filter(app -> !isSuppressed(app, replacements))
                // Deterministic order: category first so a transit app precedes the payment card it
                // is bought with, then slug so two apps of one category never swap between runs.
                .sorted(Comparator.comparing((TravelApp app) -> app.category().ordinal())
                        .thenComparing(TravelApp::slug))
                .toList();
    }

    /**
     * Whether the corpus says this app is replaced in this market.
     *
     * <p>Matched on {@code slug}, which is the stable handle a replacement's {@code replacedAppKey}
     * names — the display name is locale-dependent and would make suppression fail exactly where it
     * matters most.
     */
    private static boolean isSuppressed(TravelApp app, List<TravelAppReplacement> replacements) {
        return replacements.stream().anyMatch(replacement -> replacement.suppresses(app.slug()));
    }

    /**
     * Which app categories serve a mode.
     *
     * <p>A deliberate, hand-written mapping rather than something derived: the useful answer for a
     * metro leg is a transit app <em>and</em> the payment card it needs, which is a product judgement
     * no schema encodes. {@code PAYMENT} rides along with public transport for exactly that reason —
     * the IC card is the thing a traveller does not know to ask for.
     */
    private static Set<TravelAppCategory> categoriesFor(TransportKind mode) {
        return switch (mode) {
            case METRO, TRAIN, BUS, TRAM, FERRY -> new LinkedHashSet<>(
                    List.of(TravelAppCategory.TRANSIT, TravelAppCategory.PAYMENT));
            case TAXI, RIDESHARE, CAR -> new LinkedHashSet<>(
                    List.of(TravelAppCategory.RIDEHAILING, TravelAppCategory.PAYMENT));
            // Navigation only. A walking leg does not need a payment app, and a translation app is
            // not a mobility recommendation however useful it is on the trip as a whole.
            case WALK, BIKE -> new LinkedHashSet<>(List.of(TravelAppCategory.NAVIGATION));
        };
    }
}
