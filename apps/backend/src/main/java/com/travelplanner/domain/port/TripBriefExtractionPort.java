package com.travelplanner.domain.port;

import com.travelplanner.domain.model.TripBriefExtraction;
import com.travelplanner.domain.model.TripBriefExtractionRequest;

/**
 * Turns a traveller's sentence into a validated {@code TripBrief} draft (task 19, UC-C1-02).
 *
 * <p>It exists so the application layer can extract without importing {@code ai/} — the boundary
 * ArchUnit's {@code applicationDependsOnDomainOnly} enforces. The service knows it asked for an
 * extraction; it does not know whether a model, a rules engine, or a recorded fixture answered.
 *
 * <h2>Rules for implementations</h2>
 *
 * <ul>
 *   <li><strong>Never throws for a model failure.</strong> A timeout, an unreachable provider, or
 *       output that survived the bounded repair attempt all return
 *       {@link TripBriefExtraction#fallback} with the error code attached. Extraction is an
 *       enhancement over a form that already works, so a provider outage must degrade to that form
 *       rather than turn a working intake screen into a 502.</li>
 *   <li><strong>Bounded, always.</strong> One structured-completion call per request, itself capped
 *       at one repair attempt. There is no loop here that a malformed response can extend, because
 *       an unbounded retry against a metered API is a cost incident rather than a bug.</li>
 *   <li><strong>User text is data.</strong> It goes in a user message, inside a delimiter block,
 *       and never into the system or schema instructions (PLAN §9 "LLM injection").</li>
 *   <li><strong>No side effects.</strong> No research is started, nothing is booked, nothing is
 *       persisted. The caller decides what to do with the result.</li>
 * </ul>
 *
 * <h2>Rules for callers</h2>
 *
 * <p>Never inside {@code @Transactional}. The call takes seconds and would hold a pooled database
 * connection for all of them (AGENTS.md; AI-AGENT-WORKFLOW §3 step 4).
 */
public interface TripBriefExtractionPort {

    /** @return a validated draft, or the deterministic fallback. Never {@code null}. */
    TripBriefExtraction extract(TripBriefExtractionRequest request);
}
