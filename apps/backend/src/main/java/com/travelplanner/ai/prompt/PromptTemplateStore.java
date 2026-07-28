package com.travelplanner.ai.prompt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The versioned prompt registry (PLAN §5.3; §4.0.8 "registries: extend without branching").
 *
 * <p><strong>It ships empty.</strong> Task 14's "Do not" list forbids creating TripBrief, research,
 * itinerary, or chat prompts — those belong to tasks 19, 25, 30, and 21. What this task owns is the
 * registry those tasks register into, so adding a prompt is a new file plus a
 * {@link #register(PromptTemplate)} call rather than an edit to a shared switch statement.
 *
 * <p>Highest version wins on lookup, and older versions stay resolvable by exact version. That is
 * what makes a rollback a configuration change instead of a deploy, and what lets an
 * {@code ai_call_log} row from last week still be traced to the text that produced it.
 *
 * <p>Provider variants are resolved with fallback: ask for the {@code anthropic} variant, get it if
 * one was registered, otherwise get the neutral text. Requiring every prompt to be authored per
 * provider would triple the authoring cost for the majority of prompts where it makes no difference.
 */
public final class PromptTemplateStore {

    private final Map<String, Map<Integer, List<PromptTemplate>>> templates = new ConcurrentHashMap<>();

    /** @throws IllegalStateException when the same id, version, and variant is registered twice */
    public void register(PromptTemplate template) {
        List<PromptTemplate> variants = templates
                .computeIfAbsent(template.id(), key -> new ConcurrentHashMap<>())
                .computeIfAbsent(template.version(), key -> new java.util.concurrent.CopyOnWriteArrayList<>());
        boolean duplicate = variants.stream()
                .anyMatch(existing -> java.util.Objects.equals(existing.variant(), template.variant()));
        if (duplicate) {
            // Two prompts under one identity means the effective text depends on bean creation order.
            throw new IllegalStateException("Prompt '" + template.id() + "' v" + template.version()
                    + " is already registered for variant " + template.variant());
        }
        variants.add(template);
    }

    /** The newest version, provider-neutral. */
    public PromptTemplate latest(String id) {
        return latest(id, null);
    }

    /** The newest version, preferring {@code variant} and falling back to the neutral text. */
    public PromptTemplate latest(String id, String variant) {
        Map<Integer, List<PromptTemplate>> versions = versionsOf(id);
        int newest = versions.keySet().stream().mapToInt(Integer::intValue).max().orElseThrow();
        return resolve(id, newest, variant);
    }

    /** An exact version — what a rollback or a reproduction of an old log row needs. */
    public PromptTemplate version(String id, int version) {
        return resolve(id, version, null);
    }

    /** Every registered id. Used by the task 15 gate that asserts prompts are covered by evals. */
    public Collection<String> ids() {
        return List.copyOf(templates.keySet());
    }

    private PromptTemplate resolve(String id, int version, String variant) {
        List<PromptTemplate> variants = versionsOf(id).get(version);
        if (variants == null) {
            throw new IllegalArgumentException("No prompt '" + id + "' at version " + version);
        }
        return variants.stream()
                .filter(template -> java.util.Objects.equals(template.variant(), variant))
                .findFirst()
                .or(() -> variants.stream().filter(template -> template.variant() == null).findFirst())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Prompt '" + id + "' v" + version + " has no variant " + variant
                                + " and no provider-neutral text"));
    }

    private Map<Integer, List<PromptTemplate>> versionsOf(String id) {
        Map<Integer, List<PromptTemplate>> versions = templates.get(id);
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("No prompt registered under '" + id + "'");
        }
        return versions;
    }
}
