package com.travelplanner.api.dto.destination;

import com.travelplanner.domain.model.Destination;
import java.util.List;

/**
 * The body of {@code GET /destinations/supported} (ADR 010 §4).
 *
 * <p><strong>An object, not a bare array.</strong> A top-level array cannot gain a field, and this
 * response wanted one: {@code sample_data}, the flag ADR 010 §3 requires in development. That is
 * now the second key, which is the whole argument for having wrapped it — adding it to a bare array
 * would have been a breaking change for every generated client.
 *
 * <p><strong>Not paginated.</strong> ADR 010 §1 fixes v1 coverage at three destinations and the
 * same list is what a refusal has to name inline, so {@code PageMetadata} would add three fields no
 * caller could act on. If coverage ever outgrows one response this becomes a paginated list and the
 * key stays {@code destinations}.
 *
 * @param destinations every {@code FULL} destination, in the port's stable order. Empty means
 *        nothing is curated yet — which is a truthful answer, and the one that makes the picker
 *        say "no destinations available" instead of inventing options
 * @param sampleData ADR 010 §3 — true when this deployment's knowledge is the sample seed, so the
 *        picker can raise its persistent banner. Never true in production: the context refuses to
 *        start with the sample seed under the {@code prod} profile
 */
public record SupportedDestinationsResponse(
        List<SupportedDestinationResponse> destinations, boolean sampleData) {

    public static SupportedDestinationsResponse from(
            List<Destination> destinations, boolean sampleData) {
        return new SupportedDestinationsResponse(
                destinations.stream().map(SupportedDestinationResponse::from).toList(), sampleData);
    }
}
