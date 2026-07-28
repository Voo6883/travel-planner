/**
 * AI observability (PLAN §5.3; backlog S2-5): tokens, latency, provider, model, cost →
 * {@code ai_call_log}.
 *
 * <p>The package exists to make one guarantee reviewable in one place: <strong>no prompt text, no
 * completion text, and no PII is ever recorded</strong> (AI-AGENT-WORKFLOW A4, PLAN §9). Prompts are
 * reduced to a SHA-256 by {@code PromptHasher}, and {@code AiCallRecorder} is the only constructor of
 * {@code AiCallRecord} — a type that has no field capable of holding the text in the first place.
 */
package com.travelplanner.ai.observability;
