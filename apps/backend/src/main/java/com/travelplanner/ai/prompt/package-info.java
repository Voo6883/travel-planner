/**
 * The versioned prompt registry (PLAN §5.3 {@code PromptTemplateStore}).
 *
 * <p>Ships with <strong>no prompts</strong>. Task 14's "Do not" list reserves TripBrief, research,
 * itinerary, and chat prompts for tasks 19, 25, 30, and 21; what this task owns is the registry they
 * register into, plus strict rendering so a missing variable fails loudly instead of reaching a model
 * as a literal {@code {{placeholder}}}.
 *
 * <p>PLAN §9 also makes this the trigger for the eval harness: any change under {@code ai/prompt/}
 * runs it in CI (§15.3).
 */
package com.travelplanner.ai.prompt;
