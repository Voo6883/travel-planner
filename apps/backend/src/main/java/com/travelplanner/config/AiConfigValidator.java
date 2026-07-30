package com.travelplanner.config;

import com.travelplanner.domain.ai.EmbeddingModelRef;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Turns an incoherent AI configuration into a startup failure (task 14 — "configuration validation
 * ensuring selected providers have required credentials").
 *
 * <p>Every rule here protects against a failure that is otherwise <em>silent until it matters</em>:
 *
 * <ul>
 *   <li>A provider selected without a key fails on the first user's first message, in production,
 *       at whatever hour that happens to be — not at deploy time when someone is watching.</li>
 *   <li>A routing entry naming a provider that does not exist ({@code reserch: anthropic}) falls
 *       through to the default, and the feature quietly runs on the wrong model forever.</li>
 *   <li>A mismatched embedding model and dimension writes vectors that cannot be compared with the
 *       ones already in the index. Nothing throws; retrieval just returns the wrong rows.</li>
 * </ul>
 *
 * <p>A plain static class rather than {@code @Validated} annotations: the rules are conditional
 * ("a key is required <em>if</em> this provider is selected <em>anywhere</em>") and the message has
 * to say which knob to turn. Bean Validation can express a non-blank field; it cannot say "you set
 * {@code routing.research=openai}, so {@code OPENAI_API_KEY} is now mandatory".
 */
final class AiConfigValidator {

    /** Mirrors {@link KnowledgeConfigValidator#PROD_PROFILE} — one spelling of "this is real". */
    static final String PROD_PROFILE = "prod";

    private AiConfigValidator() {
    }

    /**
     * @param activeProfiles from {@code Environment.getActiveProfiles()}
     * @throws IllegalStateException with an actionable message; the context then fails to start
     */
    static void validate(AiProperties properties, String[] activeProfiles) {
        boolean production = isProduction(activeProfiles);
        Set<String> selected = selectedChatProviders(properties);
        for (String provider : selected) {
            requireKnownProvider(provider);
            requireCredential(properties, provider);
            if (production) {
                refuseStubInProduction(provider, "a chat provider");
            }
        }
        validateEmbeddings(properties, production);
    }

    /**
     * ADR 010 §3's rule for the knowledge base, applied to the model itself.
     *
     * <p>The stub is the right default everywhere else — it is what keeps live keys out of CI — but
     * in production it converts a missing credential from a boot failure into a running application
     * that answers every traveller with {@code [stub] no model configured}. Both are outages; only
     * one of them is visible to whoever deployed it. The credential check above cannot catch this,
     * because {@code stub} legitimately has no credential to be missing.
     */
    private static void refuseStubInProduction(String provider, String role) {
        if (AiProperties.STUB_PROVIDER.equals(provider)) {
            throw new IllegalStateException("The stub LLM is selected as " + role + " under the '"
                    + PROD_PROFILE + "' profile. It returns placeholder text rather than model "
                    + "output, so a deployment missing " + AiProperties.ANTHROPIC_PROVIDER + " or "
                    + AiProperties.OPENAI_PROVIDER + " credentials would serve every traveller a "
                    + "visible stub instead of failing at boot. Configure a real provider and its "
                    + "API key; the stub is for test, dev, docker, and local only.");
        }
    }

    private static boolean isProduction(String[] activeProfiles) {
        if (activeProfiles == null) {
            return false;
        }
        for (String profile : activeProfiles) {
            if (PROD_PROFILE.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The default plus every routed provider. Routing is included because a key that is only needed
     * by one feature is still needed at startup — discovering it when a user first opens the
     * research screen is not meaningfully better than discovering it at boot.
     */
    private static Set<String> selectedChatProviders(AiProperties properties) {
        Set<String> selected = new LinkedHashSet<>();
        selected.add(properties.getProvider().getDefaultProvider());
        for (Map.Entry<String, String> entry : properties.getRouting().entrySet()) {
            String provider = entry.getValue();
            if (provider == null || provider.isBlank()) {
                throw new IllegalStateException(
                        "travelplanner.ai.routing." + entry.getKey() + " is empty. Remove the entry "
                                + "to use the default provider, or name a provider.");
            }
            selected.add(provider.trim());
        }
        return selected;
    }

    private static void requireKnownProvider(String provider) {
        boolean known = AiProperties.STUB_PROVIDER.equals(provider)
                || AiProperties.ANTHROPIC_PROVIDER.equals(provider)
                || AiProperties.OPENAI_PROVIDER.equals(provider);
        if (!known) {
            throw new IllegalStateException("Unknown AI provider '" + provider + "'. Supported: "
                    + AiProperties.STUB_PROVIDER + ", " + AiProperties.ANTHROPIC_PROVIDER + ", "
                    + AiProperties.OPENAI_PROVIDER + ".");
        }
    }

    private static void requireCredential(AiProperties properties, String provider) {
        if (AiProperties.ANTHROPIC_PROVIDER.equals(provider)
                && properties.getAnthropic().getApiKey().isBlank()) {
            throw new IllegalStateException(missingKey("anthropic", "ANTHROPIC_API_KEY"));
        }
        if (AiProperties.OPENAI_PROVIDER.equals(provider)
                && properties.getOpenai().getApiKey().isBlank()) {
            throw new IllegalStateException(missingKey("openai", "OPENAI_API_KEY"));
        }
    }

    /**
     * The embedding pin (ADR 010 §5).
     *
     * <p>The dimension check is delegated to {@link EmbeddingModelRef}, so the rule lives with the
     * type that carries it and cannot be bypassed by constructing a reference some other way.
     */
    private static void validateEmbeddings(AiProperties properties, boolean production) {
        AiProperties.Embeddings embeddings = properties.getEmbeddings();
        String provider = embeddings.getProvider();
        requireKnownProvider(provider);
        if (production) {
            // A stub embedding adapter is worse than a stub chat model, not better: its vectors are
            // deterministic noise, so retrieval returns confidently ranked nonsense and nothing
            // anywhere prints the word "stub".
            refuseStubInProduction(provider, "the embedding provider");
        }
        if (AiProperties.ANTHROPIC_PROVIDER.equals(provider)) {
            throw new IllegalStateException(
                    "travelplanner.ai.embeddings.provider=anthropic is not supported: Anthropic "
                            + "serves no embedding model, and ADR 010 §5 pins "
                            + EmbeddingModelRef.PINNED_MODEL + " on "
                            + EmbeddingModelRef.PINNED_PROVIDER + ".");
        }
        requireCredential(properties, provider);

        // Constructing the reference is the validation: the record rejects a model/dimension pair
        // that would produce vectors incomparable with what the index already holds.
        EmbeddingModelRef configured = new EmbeddingModelRef(
                provider, embeddings.getModel(), embeddings.getDimension());

        if (!AiProperties.STUB_PROVIDER.equals(provider) && !configured.isCompatibleWith(EmbeddingModelRef.pinned())) {
            throw new IllegalStateException(
                    "travelplanner.ai.embeddings is " + configured.qualifiedName() + "/"
                            + configured.dimension() + ", but ADR 010 §5 pins "
                            + EmbeddingModelRef.pinned().qualifiedName() + "/"
                            + EmbeddingModelRef.PINNED_DIMENSION
                            + ". Changing the embedding model requires an additive migration and a "
                            + "backfill (ADR 010 §5, 'never an in-place rebuild'), not a config edit.");
        }
    }

    /**
     * Deliberately does not offer the stub as a remedy. It used to, and describing it as "the
     * default, which needs no credentials" is an instruction to disable the model — correct advice
     * for a developer and exactly the wrong advice for whoever is reading this message off a
     * production boot failure.
     */
    private static String missingKey(String provider, String envVar) {
        return "An AI provider is set to '" + provider + "' but " + envVar + " is not set. Set the "
                + "key. Outside production, travelplanner.ai.provider.default="
                + AiProperties.STUB_PROVIDER + " runs without credentials; under the '"
                + PROD_PROFILE + "' profile that is refused.";
    }
}
