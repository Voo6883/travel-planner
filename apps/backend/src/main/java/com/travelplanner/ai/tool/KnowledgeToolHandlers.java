package com.travelplanner.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.domain.model.DestinationGuide;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.PriceObservation;
import com.travelplanner.domain.model.RouteSegment;
import com.travelplanner.domain.model.SeasonalityMonth;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.domain.port.KnowledgePort;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** KnowledgePort executors behind {@link KnowledgeResearchTools}. */
final class KnowledgeToolHandlers {

    private static final String SLUG_OR_ID = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["destination_id"],
              "properties": {
                "destination_id": { "type": "string", "format": "uuid" }
              }
            }
            """;

    private final KnowledgePort knowledge;
    private final ResearchToolJson json;

    KnowledgeToolHandlers(KnowledgePort knowledge, ResearchToolJson json) {
        this.knowledge = knowledge;
        this.json = json;
    }

    KnowledgeResearchTools.ResearchTool guide() {
        return tool(KnowledgeResearchTools.GET_DESTINATION_GUIDE,
                "Destination overview, food, and practical notes from the TKB.",
                SLUG_OR_ID, args -> {
                    UUID destinationId = uuid(args, "destination_id");
                    Optional<DestinationGuide> guide = knowledge.findGuide(destinationId, "en");
                    if (guide.isEmpty()) {
                        return json.object(node -> node.put("found", false)).toString();
                    }
                    DestinationGuide g = guide.get();
                    return json.object(node -> {
                        node.put("found", true);
                        node.put("overview", g.overview());
                        g.foodIfPresent().ifPresent(v -> node.put("food", v));
                        g.practicalIfPresent().ifPresent(v -> node.put("practical", v));
                        putProvenance(node, g.provenance());
                    }).toString();
                });
    }

    KnowledgeResearchTools.ResearchTool areas() {
        return tool(KnowledgeResearchTools.GET_AREAS,
                "Neighbourhoods within a destination (stay / explore / day-trip).",
                SLUG_OR_ID, args -> {
                    List<DestinationArea> areas = knowledge.findAreas(uuid(args, "destination_id"));
                    return json.object(node -> {
                        ArrayNode list = node.putArray("areas");
                        for (DestinationArea area : areas) {
                            ObjectNode row = list.addObject();
                            row.put("slug", area.slug());
                            row.put("name", area.name());
                            area.descriptionIfPresent().ifPresent(v -> row.put("description", v));
                            putProvenance(row, area.provenance());
                        }
                    }).toString();
                });
    }

    KnowledgeResearchTools.ResearchTool foodPois() {
        return poisTool(KnowledgeResearchTools.GET_FOOD_POIS,
                "Food POIs for a destination from the TKB.", Optional.of(PoiCategory.FOOD));
    }

    KnowledgeResearchTools.ResearchTool pois() {
        return poisTool(KnowledgeResearchTools.GET_POIS,
                "POIs for a destination, optionally filtered by category.", Optional.empty());
    }

    private KnowledgeResearchTools.ResearchTool poisTool(
            String name, String description, Optional<PoiCategory> forced) {
        String schema = forced.isPresent() ? SLUG_OR_ID : """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["destination_id"],
                  "properties": {
                    "destination_id": { "type": "string", "format": "uuid" },
                    "category": {
                      "type": "string",
                      "enum": ["FOOD","SIGHT","MUSEUM","NATURE","SHOPPING","NIGHTLIFE","EXPERIENCE"]
                    }
                  }
                }
                """;
        return tool(name, description, schema, args -> {
            UUID destinationId = uuid(args, "destination_id");
            Optional<PoiCategory> category = forced.or(() -> enumOpt(args, "category", PoiCategory.class));
            List<Poi> pois = knowledge.findPois(destinationId, category);
            return json.object(node -> {
                ArrayNode list = node.putArray("pois");
                int limit = Math.min(pois.size(), 12);
                for (int i = 0; i < limit; i++) {
                    Poi poi = pois.get(i);
                    ObjectNode row = list.addObject();
                    row.put("slug", poi.slug());
                    row.put("name", poi.name());
                    row.put("category", poi.category().name());
                    row.put("description", poi.description() == null ? "" : poi.description());
                    putProvenance(row, poi.provenance());
                }
            }).toString();
        });
    }

    KnowledgeResearchTools.ResearchTool seasonality() {
        return tool(KnowledgeResearchTools.GET_SEASONALITY,
                "Twelve-month seasonality bands for a FULL destination.",
                SLUG_OR_ID, args -> {
                    List<SeasonalityMonth> months =
                            knowledge.findSeasonality(uuid(args, "destination_id"));
                    return json.object(node -> {
                        ArrayNode list = node.putArray("months");
                        for (SeasonalityMonth month : months) {
                            ObjectNode row = list.addObject();
                            row.put("month", month.month());
                            row.put("weather", month.weatherBand().name());
                            row.put("crowd", month.crowdBand().name());
                            row.put("price", month.priceBand().name());
                            putProvenance(row, month.provenance());
                        }
                    }).toString();
                });
    }

    KnowledgeResearchTools.ResearchTool prices() {
        String schema = """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["destination_id", "category"],
                  "properties": {
                    "destination_id": { "type": "string", "format": "uuid" },
                    "category": { "type": "string", "enum": ["HOTEL_NIGHT","MEAL_MID_RANGE","TRANSIT_DAY_PASS"] }
                  }
                }
                """;
        return tool(KnowledgeResearchTools.GET_PRICE_HISTORY,
                "Historical price observations for one cost category.",
                schema, args -> {
                    String category = text(args, "category");
                    List<PriceObservation> prices =
                            knowledge.findPriceHistory(uuid(args, "destination_id"), category);
                    return json.object(node -> {
                        ArrayNode list = node.putArray("prices");
                        int limit = Math.min(prices.size(), 8);
                        for (int i = 0; i < limit; i++) {
                            PriceObservation price = prices.get(i);
                            ObjectNode row = list.addObject();
                            row.put("category", price.category());
                            row.put("amount", price.amount().amount().toPlainString());
                            row.put("currency", price.amount().currency().getCurrencyCode());
                            row.put("observed_on", price.observedOn().toString());
                            putProvenance(row, price.provenance());
                        }
                    }).toString();
                });
    }

    KnowledgeResearchTools.ResearchTool transport() {
        return tool(KnowledgeResearchTools.GET_TRANSPORT_MODES,
                "Transport modes available at a destination.",
                SLUG_OR_ID, args -> {
                    List<TransportMode> modes =
                            knowledge.findTransportModes(uuid(args, "destination_id"));
                    return json.object(node -> {
                        ArrayNode list = node.putArray("modes");
                        for (TransportMode mode : modes) {
                            ObjectNode row = list.addObject();
                            row.put("kind", mode.kind().name());
                            row.put("name", mode.name());
                            putProvenance(row, mode.provenance());
                        }
                    }).toString();
                });
    }

    KnowledgeResearchTools.ResearchTool routes() {
        return tool(KnowledgeResearchTools.GET_ROUTE_SEGMENTS,
                "Curated A→B route segments within a destination.",
                SLUG_OR_ID, args -> {
                    List<RouteSegment> segments =
                            knowledge.findRouteSegments(uuid(args, "destination_id"));
                    return json.object(node -> {
                        ArrayNode list = node.putArray("routes");
                        int limit = Math.min(segments.size(), 10);
                        for (int i = 0; i < limit; i++) {
                            RouteSegment segment = segments.get(i);
                            ObjectNode row = list.addObject();
                            row.put("duration_mins", segment.durationMinutes());
                            row.put("estimated", segment.estimated());
                            putProvenance(row, segment.provenance());
                        }
                    }).toString();
                });
    }

    KnowledgeResearchTools.ResearchTool apps() {
        String schema = """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["country_code"],
                  "properties": {
                    "country_code": { "type": "string", "minLength": 2, "maxLength": 2 }
                  }
                }
                """;
        return tool(KnowledgeResearchTools.GET_TRAVEL_APPS,
                "Locale app pack for a country (maps, transit, ride-hail, pay…).",
                schema, args -> {
                    String country = text(args, "country_code").toUpperCase();
                    List<TravelApp> apps = knowledge.findTravelApps(country);
                    return json.object(node -> {
                        ArrayNode list = node.putArray("apps");
                        for (TravelApp app : apps) {
                            ObjectNode row = list.addObject();
                            row.put("slug", app.slug());
                            row.put("name", app.name());
                            row.put("category", app.category().name());
                            putProvenance(row, app.provenance());
                        }
                    }).toString();
                });
    }

    private KnowledgeResearchTools.ResearchTool tool(
            String name, String description, String schema,
            java.util.function.Function<JsonNode, String> body) {
        ToolSpec spec = new ToolSpec(name, description, schema);
        return new KnowledgeResearchTools.ResearchTool(spec, raw -> {
            JsonNode args = json.parseObject(raw);
            return body.apply(args);
        });
    }

    private void putProvenance(ObjectNode node, KnowledgeProvenance provenance) {
        node.put("source_ref", provenance.sourceRef());
        if (provenance.sourceUrl() != null && !provenance.sourceUrl().isBlank()) {
            node.put("source_url", provenance.sourceUrl());
        }
    }

    private UUID uuid(JsonNode args, String field) {
        JsonNode value = args.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return UUID.fromString(value.asText());
    }

    private String text(JsonNode args, String field) {
        JsonNode value = args.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.asText().trim();
    }

    private <E extends Enum<E>> Optional<E> enumOpt(JsonNode args, String field, Class<E> type) {
        JsonNode value = args.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Enum.valueOf(type, value.asText().trim().toUpperCase()));
    }
}
