package com.travelplanner.application.trip;

import com.travelplanner.domain.model.TripBriefExtraction;
import java.util.List;

/**
 * What one extraction did to a trip — the saved state plus why it looks that way.
 *
 * <p>The two halves travel together for the same reason {@link TripBriefView}'s three do: a caller
 * that had the new brief but not the extraction could not tell "we have no dates because you have
 * not said" from "we have no dates because the assistant timed out", and would render the same
 * blank form for both. Tasks 21 and 22 need that distinction to write the sentence above the form.
 *
 * @param view the persisted brief, the trip status, and the outstanding questions — exactly what
 *     {@code GET .../brief} would now return, so a chat turn and a form load cannot disagree
 * @param extraction what the model produced and what was refused, including
 *     {@code extraction.promptVersion()} and, on failure, {@code extraction.failureCode()}
 * @param uncoveredDestinations slugs the traveller named that the knowledge base has never curated
 *     (ADR 010 §4). They are <strong>not</strong> in {@code view}: an uncovered destination cannot
 *     be researched honestly, so it is dropped from the brief and reported here instead, which lets
 *     the caller answer "we do not cover Osaka yet" while keeping every other field the traveller
 *     just supplied. Empty in the ordinary case
 */
public record TripBriefExtractionResult(TripBriefView view, TripBriefExtraction extraction,
        List<String> uncoveredDestinations) {

    public TripBriefExtractionResult {
        uncoveredDestinations =
                uncoveredDestinations == null ? List.of() : List.copyOf(uncoveredDestinations);
    }
}
