package com.travelplanner.domain.port;

import com.travelplanner.domain.model.ItineraryGenerationRequest;
import com.travelplanner.domain.model.ItineraryProposal;

/**
 * The C3 itinerary agent (task 30, PLAN §4.1, UC-C3-01).
 *
 * <p>Application calls this port and never imports {@code ai/} — the boundary ArchUnit enforces,
 * exactly as {@link TravelResearchAgentPort} does for C2.
 *
 * <h2>What an implementation decides, and what it may not</h2>
 *
 * <p>It chooses <strong>which</strong> POIs, <strong>in what order</strong>, grouped into days, and
 * writes the prose. It does <strong>not</strong> decide times, durations, travel or feasibility.
 * Those belong to {@code ItineraryScheduler} (task 28) and {@code RouteChoicePolicy} (task 29),
 * which run after this port returns and are authoritative over it. An implementation that returned
 * a schedule would be asserting arithmetic nobody can check; the {@link ItineraryProposal} type has
 * nowhere to put one, which is the enforcement.
 *
 * <h2>Rules for implementations</h2>
 *
 * <ul>
 *   <li><strong>Every POI must come from the request.</strong> No lookups, no invention. The
 *       request carries the full candidate list precisely so a proposal cannot reference anything
 *       else, and guardrails reject the whole proposal if one does.</li>
 *   <li><strong>Fail closed.</strong> Malformed output or provider failure throws; the caller
 *       produces a typed failure and the trip stays where it was.</li>
 *   <li><strong>No side effects.</strong> No persistence, no status change — the service owns
 *       those.</li>
 *   <li><strong>Never inside {@code @Transactional}.</strong> The brief says so in as many words,
 *       and F-41 is why.</li>
 * </ul>
 */
public interface ItineraryAgentPort {

    /**
     * Proposes a day-by-day selection and ordering.
     *
     * @throws com.travelplanner.domain.exception.AiProviderException on provider failure
     * @throws com.travelplanner.domain.exception.ValidationFailedException when the model's own
     *         output is unusable after the bounded repair attempt
     */
    ItineraryProposal propose(ItineraryGenerationRequest request);
}
