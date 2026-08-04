package com.travelplanner.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import java.util.function.Consumer;

/** Small Jackson helpers shared by research tools — keeps handlers under the method-length budget. */
public final class ResearchToolJson {

    private final ObjectMapper mapper;

    public ResearchToolJson(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public ObjectNode object(Consumer<ObjectNode> populate) {
        ObjectNode node = mapper.createObjectNode();
        populate.accept(node);
        return node;
    }

    public JsonNode parseObject(String raw) {
        try {
            JsonNode node = mapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
            if (!node.isObject()) {
                throw new IllegalArgumentException("tool arguments must be a JSON object");
            }
            return node;
        } catch (IllegalArgumentException bad) {
            throw bad;
        } catch (Exception failure) {
            throw new IllegalArgumentException("tool arguments are not valid JSON", failure);
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
