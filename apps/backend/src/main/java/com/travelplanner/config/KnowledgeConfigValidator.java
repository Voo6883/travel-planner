package com.travelplanner.config;

import java.util.List;

/**
 * Refuses to start a production deployment whose travel knowledge is fabricated (ADR 010 §3).
 *
 * <p><strong>Why this exists.</strong> PLAN §4.1.0 makes three non-negotiable claims: retrieve
 * before claiming, never invent facts, provenance on every field. A {@code Stub*} knowledge adapter
 * inverts all three at once — it emits guides, POIs, and prices carrying {@code source_refs[]} that
 * point at nothing. §4.0.7's "default external adapters to stubs" is right for flights and hotels,
 * where a stub returns an obviously fake fare nobody can book; it is wrong for the knowledge base,
 * where a stub returns a plausible sentence about a restaurant that does not exist and a traveller
 * acts on it.
 *
 * <p>The failure is silent by construction. Stub rows look exactly like curated rows to every layer
 * above the port, and the only visible difference — the reserved {@code stub:sample} source ref —
 * is a value nobody reads until somebody asks where a claim came from. So the check cannot be "does
 * the data look wrong at request time"; it has to be "is the wiring capable of producing it at
 * all", asserted once, at boot.
 *
 * <p>Development is deliberately untouched. ADR 010 §3 permits the stub there, flagged
 * {@code sample_data: true} with a persistent banner, because C2 and C3 have to be workable before
 * curation completes. The rule is about environments that <em>look real</em>, which is exactly what
 * the profile distinction encodes.
 *
 * <p>A plain static class called from {@link KnowledgeConfig}, mirroring {@link MailConfigValidator}
 * and {@code AiConfigValidator}: the rule is conditional on the active profile and the message has
 * to name the knob, which is more than a {@code @Validated} annotation can express.
 */
final class KnowledgeConfigValidator {

    static final String PROD_PROFILE = "prod";

    /**
     * The switch that enables the curated-sample seed loader.
     *
     * <p>Task 17 splits the seed loader and this guard across two changes, so the property may not
     * be bound by a {@code @ConfigurationProperties} class yet. It is read from {@link
     * org.springframework.core.env.Environment} by name and defaults to {@code false}, which means
     * the guard is correct before the loader lands and stays correct after — and an operator who
     * sets it in a prod environment file is refused either way.
     */
    static final String SAMPLE_SEED_PROPERTY = "travelplanner.knowledge.sample-seed.enabled";

    /**
     * ADR 010 §3 names the offender by shape — "any {@code Stub*} knowledge adapter" — rather than
     * by a list of classes. Matching the prefix is what makes the rule survive a stub added by a
     * later task: a new {@code StubTravelAppAdapter} is caught the day it is written, not the day
     * somebody remembers to extend an allow-list.
     */
    private static final String STUB_PREFIX = "Stub";

    private KnowledgeConfigValidator() {
    }

    /**
     * @param activeProfiles from {@code Environment.getActiveProfiles()}
     * @param sampleSeedEnabled the resolved value of {@link #SAMPLE_SEED_PROPERTY}
     * @param knowledgeAdapters simple class names of every bean implementing {@code KnowledgePort}
     * @throws IllegalStateException with an actionable message; the context then fails to start
     */
    static void validate(String[] activeProfiles, boolean sampleSeedEnabled,
            List<String> knowledgeAdapters) {
        if (!isProduction(activeProfiles)) {
            return;
        }
        if (sampleSeedEnabled) {
            throw new IllegalStateException(
                    SAMPLE_SEED_PROPERTY + "=true under the '" + PROD_PROFILE + "' profile. The "
                            + "sample seed cites the reserved source ref 'stub:sample', so every "
                            + "guide, POI, and price it backs is a fabricated citation (ADR 010 §3). "
                            + "Set " + SAMPLE_SEED_PROPERTY + "=false and load a curated seed.");
        }
        for (String adapter : knowledgeAdapters) {
            if (isStubAdapter(adapter)) {
                throw new IllegalStateException(
                        adapter + " is wired as the KnowledgePort under the '" + PROD_PROFILE
                                + "' profile. A stub knowledge adapter invents guides, POIs, and "
                                + "prices carrying source_refs that point at nothing — the exact "
                                + "failure PLAN §4.1.0 forbids, and ADR 010 §3 refuses in any "
                                + "environment that looks real. Remove the stub from the production "
                                + "wiring and set " + SAMPLE_SEED_PROPERTY + "=false.");
            }
        }
    }

    /** ADR 010 §3 matches on the {@code Stub*} shape, not on a fixed list of class names. */
    static boolean isStubAdapter(String simpleClassName) {
        return simpleClassName != null && simpleClassName.startsWith(STUB_PREFIX);
    }

    private static boolean isProduction(String[] activeProfiles) {
        for (String profile : activeProfiles) {
            if (PROD_PROFILE.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
