/**
 * Pure destination ranking DSA (PLAN §4.0.3, §4.1.2). No Spring, no I/O, no LLM.
 *
 * <p>Application code loads bounded candidates via {@code KnowledgePort}, then calls
 * {@link com.travelplanner.domain.algorithm.ranking.DestinationRanker}.
 */
package com.travelplanner.domain.algorithm.ranking;
