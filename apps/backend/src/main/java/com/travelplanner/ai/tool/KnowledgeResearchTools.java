package com.travelplanner.ai.tool;

import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.port.KnowledgePort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Bounded KnowledgePort tool registry for C2 research (PLAN §4.1, BACKLOG S4-4c).
 *
 * <p>Each tool is a named, schema-validated function over {@link KnowledgePort}. The agent loop
 * (or the deterministic stub) executes tools through {@link #execute(String, String)} so a free-text
 * tool name never reaches the port. Web search is registered separately as a stub supplement.
 */
public final class KnowledgeResearchTools {

    public static final String GET_DESTINATION_GUIDE = "get_destination_guide";
    public static final String GET_AREAS = "get_areas";
    public static final String GET_FOOD_POIS = "get_food_pois";
    public static final String GET_POIS = "get_pois";
    public static final String GET_SEASONALITY = "get_seasonality";
    public static final String GET_PRICE_HISTORY = "get_price_history";
    public static final String GET_TRANSPORT_MODES = "get_transport_modes";
    public static final String GET_ROUTE_SEGMENTS = "get_route_segments";
    public static final String GET_TRAVEL_APPS = "get_travel_apps";
    public static final String WEB_SEARCH_SUPPLEMENT = "web_search_supplement";

    private final Map<String, ResearchTool> tools;

    public KnowledgeResearchTools(KnowledgePort knowledge, ResearchToolJson json) {
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(json, "json");
        KnowledgeToolHandlers handlers = new KnowledgeToolHandlers(knowledge, json);
        Map<String, ResearchTool> registered = new LinkedHashMap<>();
        register(registered, handlers.guide());
        register(registered, handlers.areas());
        register(registered, handlers.foodPois());
        register(registered, handlers.pois());
        register(registered, handlers.seasonality());
        register(registered, handlers.prices());
        register(registered, handlers.transport());
        register(registered, handlers.routes());
        register(registered, handlers.apps());
        register(registered, new StubWebSearchTool(json).asTool());
        this.tools = Map.copyOf(registered);
    }

    public List<ToolSpec> specs() {
        return tools.values().stream().map(ResearchTool::spec).toList();
    }

    public String execute(String name, String argumentsJson) {
        ResearchTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("unknown research tool: " + name);
        }
        return tool.execute(argumentsJson == null ? "{}" : argumentsJson);
    }

    public boolean knows(String name) {
        return tools.containsKey(name);
    }

    private static void register(Map<String, ResearchTool> into, ResearchTool tool) {
        into.put(tool.spec().name(), tool);
    }

    /** One budgeted tool: schema + executor. */
    public record ResearchTool(ToolSpec spec, Function<String, String> executor) {

        public ResearchTool {
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(executor, "executor");
        }

        public String execute(String argumentsJson) {
            return executor.apply(argumentsJson);
        }
    }
}
