/**
 * Pure itinerary scheduling DSA (PLAN §4.0.3 {@code domain/algorithm/scheduling/}, UC-C3). No
 * Spring, no I/O, no LLM, no route provider.
 *
 * <p>The division of labour this package exists to enforce: <strong>feasibility is decided here,
 * before anything is narrated.</strong> Task 30's agent may choose which places to visit and write
 * the prose around them, but whether a day can actually be walked is arithmetic, and arithmetic
 * that a language model performs is arithmetic nobody can check.
 *
 * <p>Three things are deliberately <em>inputs</em> rather than lookups:
 *
 * <ul>
 *   <li><strong>Travel buffers.</strong> Task 29 owns routing. A scheduler that guessed at travel
 *       time would be inventing the number the whole feature turns on.</li>
 *   <li><strong>Opening hours.</strong> Curated where known, absent where not — and absent is
 *       carried through to the result as {@code UNKNOWN_HOURS} rather than assumed open.</li>
 *   <li><strong>Visit durations.</strong> A property of the place, from the knowledge base.</li>
 * </ul>
 *
 * <p>Nothing here reaches for a clock. {@code LocalDate}/{@code LocalTime} arrive as arguments so
 * the same inputs always produce the same plan — the property the task's Definition of Done calls
 * determinism, and the reason the tests can be table-driven.
 */
package com.travelplanner.domain.algorithm.scheduling;
