/**
 * Pure mobility DSA (PLAN §4.0.3, UC-C3-09/10/11, UC-K10/K11/K14). No Spring, no I/O, no LLM, no
 * live provider.
 *
 * <p>Two decisions live here, both deterministic and both previously the kind of thing that gets
 * made ad hoc at a call site:
 *
 * <ul>
 *   <li><strong>Which route answers an A→B gap</strong> — {@code RouteChoicePolicy}, a fixed
 *       preference order over what the knowledge base actually holds, falling back through
 *       progressively weaker claims and finally to an honest refusal.</li>
 *   <li><strong>Which apps to recommend for a leg</strong> — {@code TravelAppSelector}, which
 *       enforces UC-K14's suppression so a global app is never offered in a market where it does not
 *       work.</li>
 * </ul>
 *
 * <p>The application layer loads candidates through {@code KnowledgePort} and hands them over
 * complete, exactly as {@code domain.algorithm.scheduling} is fed. That is what keeps both testable
 * as tables of records, and what makes "never fabricate precision" checkable rather than aspirational
 * — this package cannot reach a live provider even if a later task adds one.
 */
package com.travelplanner.domain.algorithm.mobility;
