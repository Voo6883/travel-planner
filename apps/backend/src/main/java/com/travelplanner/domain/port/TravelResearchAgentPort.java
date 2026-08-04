package com.travelplanner.domain.port;

import com.travelplanner.domain.model.TravelResearchNarratives;
import com.travelplanner.domain.model.TravelResearchRequest;

/**
 * Knowledge-grounded C2 research agent (task 25, PLAN §4.1).
 *
 * <p>Application calls this port and never imports {@code ai/} — the boundary ArchUnit enforces.
 * Implementations retrieve facts through KnowledgePort tools, then author rationale /
 * {@code traveler_guide} narrative only. They never invent numeric fit scores.
 *
 * <h2>Rules for implementations</h2>
 *
 * <ul>
 *   <li><strong>Fail closed.</strong> Malformed output, low-confidence invention, or provider
 *       failure throws — the research-job platform marks the job {@code FAILED} and recovers the
 *       trip to {@code BRIEF_COMPLETE}.</li>
 *   <li><strong>Bounded.</strong> Max tool calls, token budget, and honour {@code Thread.interrupt()}
 *       for the job wall-clock timeout.</li>
 *   <li><strong>No side effects.</strong> No persistence, no trip status changes — the handler
 *       owns those.</li>
 *   <li><strong>Never inside {@code @Transactional}.</strong></li>
 * </ul>
 */
public interface TravelResearchAgentPort {

    /**
     * Produces grounded narratives for the already-ranked destinations (or an empty list when the
     * ranking was a typed no-confident-result).
     *
     * @throws com.travelplanner.domain.exception.AiProviderException on provider/tool failure
     * @throws com.travelplanner.domain.exception.ValidationFailedException when guardrails reject
     *         invented facts
     */
    TravelResearchNarratives research(TravelResearchRequest request);
}
