/**
 * Deterministic, credential-free adapters — the default providers (PLAN §4.0.7 stub-first).
 *
 * <p>They are the default rather than test fixtures, which is what makes "no live keys in CI"
 * structural instead of aspirational: every build, every test run, and every fresh checkout uses
 * them, so nothing can quietly begin requiring a real key.
 *
 * <p>Note the boundary these stubs do <em>not</em> cross. ADR 010 §3 forbids a stub
 * <strong>knowledge</strong> adapter in anything that looks like production, because fabricated
 * guides carry fabricated {@code source_refs[]}. These are LLM and embedding stubs — they return
 * visibly marked placeholder text and content-seeded vectors, never invented travel facts.
 */
package com.travelplanner.ai.stub;
