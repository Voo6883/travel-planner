/**
 * The LangChain4j boundary. <strong>The only package permitted to import
 * {@code dev.langchain4j}</strong> (AGENTS.md non-negotiables; AI-AGENT-WORKFLOW §4).
 *
 * <p>The rule is enforced twice over. Once by convention and task 15's ArchUnit rule, and once
 * structurally: every type here except {@link com.travelplanner.ai.langchain4j.LangChain4jProviderFactory}
 * is package-private, and the factory's signatures mention only project ports. There is no
 * LangChain4j type a caller outside this package could name even if it tried.
 *
 * <p>Both providers share {@code LangChain4jLlmAdapter}, because LangChain4j has already normalised
 * Anthropic's and OpenAI's streaming dialects behind one handler interface. What genuinely differs
 * between the two is confined to three places: model construction (Anthropic requires
 * {@code max_tokens}; OpenAI must be asked for streamed usage), cached-token accounting
 * ({@code TokenUsageMapper}), and nothing else.
 */
package com.travelplanner.ai.langchain4j;
