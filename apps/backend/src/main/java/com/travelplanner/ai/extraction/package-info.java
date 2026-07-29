/**
 * Natural-language {@code TripBrief} extraction (task 19; UC-C1-01, UC-C1-02, UC-C1-05).
 *
 * <p>The adapter side of {@code TripBriefExtractionPort}: a versioned prompt, the JSON schema the
 * model is bound to, and the payload that schema deserialises into. What the extracted values are
 * allowed to be is <em>not</em> decided here — {@code TripBriefExtraction.from} in the domain runs
 * every value through the same {@code Money}, {@code DateRange}, and {@code PartySize} invariants a
 * form submission hits, so extraction has no privileged write path.
 *
 * <p>Everything model-facing in this package is versioned as one unit and pinned by a golden-file
 * gate, because a prompt is behaviour: see {@code TripBriefExtractionPrompt} for the scheme.
 */
package com.travelplanner.ai.extraction;
