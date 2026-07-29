/**
 * Prompt-safety helpers (PLAN §5.3 {@code Guardrails}, §9 "LLM injection").
 *
 * <p>Structural separation of user text from instructions, not phrase detection. A blocklist is
 * rewritten around in one attempt and refuses legitimate travellers on the way; a fence plus a
 * post-hoc assertion that nothing crossed it holds regardless of what the text says.
 */
package com.travelplanner.ai.guardrails;
