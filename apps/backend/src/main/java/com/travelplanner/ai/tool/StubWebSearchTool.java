package com.travelplanner.ai.tool;

import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.ai.tool.KnowledgeResearchTools.ResearchTool;

/**
 * Optional web-search supplement (PLAN §4.0.7 stub-first, UC-K08).
 *
 * <p>Core traveler intel comes from the TKB. This tool exists so the agent registry is complete,
 * but the stub returns an empty result with no invented events or advisories — live search waits
 * on a vendor ADR.
 */
final class StubWebSearchTool {

    private static final String SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["query"],
              "properties": {
                "query": { "type": "string", "minLength": 1, "maxLength": 240 }
              }
            }
            """;

    private final ResearchToolJson json;

    StubWebSearchTool(ResearchToolJson json) {
        this.json = json;
    }

    ResearchTool asTool() {
        ToolSpec spec = new ToolSpec(
                KnowledgeResearchTools.WEB_SEARCH_SUPPLEMENT,
                "Live supplement for events/advisories only. Stub returns no results until a "
                        + "vendor is chosen (PLAN §4.0.7).",
                SCHEMA);
        return new ResearchTool(spec, raw -> {
            json.parseObject(raw);
            return json.object(node -> {
                node.put("stub", true);
                node.putArray("results");
            }).toString();
        });
    }
}
