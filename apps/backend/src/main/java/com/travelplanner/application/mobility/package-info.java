/**
 * Route and mobility orchestration (task 29, UC-C3-09/10/11/12, UC-K10/K11/K14).
 *
 * <p>Loads the destination's mobility corpus through {@code KnowledgePort} once, then calls
 * {@code domain.algorithm.mobility.RouteChoicePolicy} for every gap. No live provider and no LLM:
 * a leg this package cannot ground in the knowledge base is published as {@code UNKNOWN}, never
 * estimated into existence.
 */
package com.travelplanner.application.mobility;
