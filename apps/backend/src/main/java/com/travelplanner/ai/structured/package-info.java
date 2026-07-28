/**
 * Typed model output (PLAN §5.2 {@code StructuredOutputRunner}, §6 item 3).
 *
 * <p>Composed over {@code LlmPort.complete} rather than implemented per adapter, so the schema
 * instruction, the JSON extraction, the single repair attempt, and the typed failure behave
 * identically for Anthropic, OpenAI, and the stub — which is what lets a test running on the stub
 * exercise the path production takes.
 */
package com.travelplanner.ai.structured;
