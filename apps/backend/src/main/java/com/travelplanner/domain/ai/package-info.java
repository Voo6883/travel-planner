/**
 * Provider-neutral AI types: the {@link com.travelplanner.domain.ai.LlmEvent} union (ADR 007),
 * prompts, options, tool specs, the embedding pin (ADR 010 §5), and the {@code ai_call_log} record.
 *
 * <p><strong>Zero framework imports</strong>, exactly like the rest of {@code domain/}: no Spring, no
 * Jackson, no LangChain4j. The one non-JDK type any of these interfaces touches is Reactor's
 * {@code Flux}, on {@code LlmPort} — mandated by ADR 007 and already present in PLAN §5.1's
 * signature, so it is part of the locked port contract rather than a framework leaking inward.
 *
 * <p>A sibling of {@code domain/model} and {@code domain/valueobject} rather than a member of
 * either: these are neither persisted aggregates nor general-purpose value objects, and folding a
 * dozen AI types into {@code valueobject/} would bury {@code Money} and {@code DateRange} among
 * them.
 */
package com.travelplanner.domain.ai;
