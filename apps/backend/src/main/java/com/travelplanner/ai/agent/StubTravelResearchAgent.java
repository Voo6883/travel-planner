package com.travelplanner.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelplanner.ai.guardrails.ResearchOutputGuardrails;
import com.travelplanner.ai.tool.KnowledgeResearchTools;
import com.travelplanner.ai.tool.ResearchEvidenceLedger;
import com.travelplanner.ai.tool.ResearchToolBudget;
import com.travelplanner.ai.tool.ResearchToolJson;
import com.travelplanner.domain.algorithm.ranking.RankedDestination;
import com.travelplanner.domain.model.DestinationNarrative;
import com.travelplanner.domain.model.TravelResearchNarratives;
import com.travelplanner.domain.model.TravelResearchRequest;
import com.travelplanner.domain.model.TravelerGuide;
import com.travelplanner.domain.port.TravelResearchAgentPort;
import com.travelplanner.domain.valueobject.RecommendationSourceRef;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic C2 research agent for CI and the default stub provider (PLAN §4.0.7).
 *
 * <p>It never calls an LLM. Every factual field is retrieved through {@link KnowledgeResearchTools}
 * against {@code KnowledgePort}, narratives are assembled from those tool results, and
 * {@link ResearchOutputGuardrails} refuse any citation that was not in the ledger.
 */
public final class StubTravelResearchAgent implements TravelResearchAgentPort {

    public static final String PROMPT_ID = "travel-research-stub";
    public static final int PROMPT_VERSION = 1;
    public static final String MODEL_NAME = "stub";

    private final KnowledgeResearchTools tools;
    private final ResearchToolJson json;
    private final int maxToolCalls;
    private final int maxTokens;

    public StubTravelResearchAgent(
            KnowledgeResearchTools tools,
            ResearchToolJson json,
            int maxToolCalls,
            int maxTokens) {
        this.tools = tools;
        this.json = json;
        this.maxToolCalls = maxToolCalls;
        this.maxTokens = maxTokens;
    }

    @Override
    public TravelResearchNarratives research(TravelResearchRequest request) {
        ResearchToolBudget budget = new ResearchToolBudget(maxToolCalls, maxTokens);
        ResearchEvidenceLedger ledger = new ResearchEvidenceLedger();
        request.ranking().ranked().forEach(r -> ledger.allowDestination(r.slug()));
        request.candidates().forEach(c -> ledger.allowDestination(c.slug()));

        if (request.ranking().noConfidentResult()) {
            request.progress().accept(100);
            return new TravelResearchNarratives(List.of(), PROMPT_ID, PROMPT_VERSION, MODEL_NAME);
        }

        List<DestinationNarrative> narratives = new ArrayList<>();
        List<RankedDestination> ranked = request.rankedDestinations();
        int total = ranked.size();
        for (int i = 0; i < total; i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("research agent interrupted");
            }
            RankedDestination destination = ranked.get(i);
            narratives.add(narrate(destination, budget, ledger));
            request.progress().accept(40 + ((i + 1) * 50) / Math.max(total, 1));
        }
        Set<UUID> allowedIds = ranked.stream().map(RankedDestination::destinationId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        ResearchOutputGuardrails.validate(narratives, allowedIds, ledger);
        return new TravelResearchNarratives(narratives, PROMPT_ID, PROMPT_VERSION, MODEL_NAME);
    }

    private DestinationNarrative narrate(
            RankedDestination destination,
            ResearchToolBudget budget,
            ResearchEvidenceLedger ledger) {
        String idJson = "{\"destination_id\":\"" + destination.destinationId() + "\"}";
        JsonNode guide = call(KnowledgeResearchTools.GET_DESTINATION_GUIDE, idJson, budget, ledger);
        JsonNode areas = call(KnowledgeResearchTools.GET_AREAS, idJson, budget, ledger);
        JsonNode food = call(KnowledgeResearchTools.GET_FOOD_POIS, idJson, budget, ledger);
        JsonNode seasonality = call(KnowledgeResearchTools.GET_SEASONALITY, idJson, budget, ledger);
        JsonNode transport = call(KnowledgeResearchTools.GET_TRANSPORT_MODES, idJson, budget, ledger);
        String appsArgs = "{\"country_code\":\"" + destination.countryCode() + "\"}";
        JsonNode apps = call(KnowledgeResearchTools.GET_TRAVEL_APPS, appsArgs, budget, ledger);

        List<RecommendationSourceRef> refs = new ArrayList<>();
        collectRefs(guide, "overview", refs, ledger);
        collectRefs(areas, "areas", refs, ledger);
        collectRefs(food, "food", refs, ledger);
        collectRefs(seasonality, "why_now", refs, ledger);
        collectRefs(transport, "mobility", refs, ledger);
        collectRefs(apps, "mobility", refs, ledger);
        if (refs.isEmpty()) {
            throw new IllegalStateException("no provenance returned for " + destination.slug());
        }

        String overview = textOr(guide, "overview",
                "Covered destination " + destination.slug() + " from the travel knowledge base.");
        String foodText = firstPoiBlurb(food);
        List<String> areaNames = namesFrom(areas, "areas");
        List<String> highlights = poiHighlights(food);
        List<TravelerGuide.LocalAppPackEntry> pack = appPack(apps);
        String mobility = mobilityBlurb(transport, pack);
        String whyNow = whyNowBlurb(seasonality);
        String practical = textOr(guide, "practical", null);

        TravelerGuide travelerGuide = new TravelerGuide(
                overview, whyNow, areaNames, foodText, highlights, mobility, practical, pack, refs);
        String rationale = "Fits the brief with score terms from destination-ranker-v1; "
                + "grounded in TKB facts for " + destination.slug() + ".";
        return new DestinationNarrative(
                destination.destinationId(),
                destination.slug(),
                rationale,
                travelerGuide,
                List.of(),
                whyNow,
                refs);
    }

    private JsonNode call(
            String toolName,
            String args,
            ResearchToolBudget budget,
            ResearchEvidenceLedger ledger) {
        String raw = tools.execute(toolName, args);
        budget.consume(toolName, args, raw);
        ledger.mergeToolJson(toolName, raw);
        return json.parseObject(raw);
    }

    private static void collectRefs(
            JsonNode node,
            String fieldGroup,
            List<RecommendationSourceRef> refs,
            ResearchEvidenceLedger ledger) {
        if (node.hasNonNull("source_ref")) {
            String ref = node.get("source_ref").asText();
            ledger.noteSourceRef(ref);
            refs.add(RecommendationSourceRef.of(ref, fieldGroup));
        }
        node.properties().forEach(entry -> {
            if (entry.getValue().isArray()) {
                for (JsonNode child : entry.getValue()) {
                    if (child.hasNonNull("source_ref")) {
                        String ref = child.get("source_ref").asText();
                        ledger.noteSourceRef(ref);
                        String url = child.hasNonNull("source_url")
                                ? child.get("source_url").asText() : null;
                        refs.add(new RecommendationSourceRef(ref, url, fieldGroup));
                    }
                    if (child.hasNonNull("slug")) {
                        // POIs / apps already scraped by ledger.mergeToolJson.
                    }
                }
            }
        });
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        if (node != null && node.hasNonNull(field) && !node.get(field).asText().isBlank()) {
            return node.get(field).asText();
        }
        return fallback;
    }

    private static List<String> namesFrom(JsonNode node, String arrayField) {
        List<String> names = new ArrayList<>();
        if (node == null || !node.has(arrayField)) {
            return names;
        }
        for (JsonNode child : node.get(arrayField)) {
            if (child.hasNonNull("name")) {
                names.add(child.get("name").asText());
            }
        }
        return names;
    }

    private static String firstPoiBlurb(JsonNode food) {
        if (food == null || !food.has("pois") || !food.get("pois").isArray()
                || food.get("pois").isEmpty()) {
            return textOr(food, "food", null);
        }
        JsonNode first = food.get("pois").get(0);
        return first.path("name").asText("Local food") + " — "
                + first.path("description").asText("see knowledge base");
    }

    private static List<String> poiHighlights(JsonNode food) {
        List<String> highlights = new ArrayList<>();
        if (food == null || !food.has("pois")) {
            return highlights;
        }
        int count = 0;
        for (JsonNode poi : food.get("pois")) {
            if (count >= 5) {
                break;
            }
            String slug = poi.path("slug").asText("");
            String name = poi.path("name").asText("");
            if (!slug.isBlank() && !name.isBlank()) {
                highlights.add(slug + ": " + name);
                count++;
            }
        }
        return highlights;
    }

    private static List<TravelerGuide.LocalAppPackEntry> appPack(JsonNode apps) {
        List<TravelerGuide.LocalAppPackEntry> pack = new ArrayList<>();
        if (apps == null || !apps.has("apps")) {
            return pack;
        }
        for (JsonNode app : apps.get("apps")) {
            String slug = app.path("slug").asText(null);
            String name = app.path("name").asText("");
            String usage = app.path("category").asText("general").toLowerCase();
            if (!name.isBlank()) {
                pack.add(new TravelerGuide.LocalAppPackEntry(usage, name, slug));
            }
        }
        return pack;
    }

    private static String mobilityBlurb(JsonNode transport, List<TravelerGuide.LocalAppPackEntry> pack) {
        List<String> modes = new ArrayList<>();
        if (transport != null && transport.has("modes")) {
            for (JsonNode mode : transport.get("modes")) {
                modes.add(mode.path("name").asText(mode.path("kind").asText("")));
            }
        }
        String modeText = modes.isEmpty() ? "Local transport as curated in the knowledge base"
                : String.join(", ", modes);
        if (pack.isEmpty()) {
            return modeText;
        }
        return modeText + "; apps: "
                + pack.stream().map(TravelerGuide.LocalAppPackEntry::name)
                        .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String whyNowBlurb(JsonNode seasonality) {
        if (seasonality == null || !seasonality.has("months") || seasonality.get("months").isEmpty()) {
            return null;
        }
        return "Seasonality bands are available in the knowledge base for trip-date months.";
    }
}
