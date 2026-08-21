package com.pharmasense.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiny fluent builder so each {@link AgentTool} doesn't hand-roll a JSON Schema map. */
public final class JsonSchema {

    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final List<String> required = new java.util.ArrayList<>();

    public static JsonSchema object() {
        return new JsonSchema();
    }

    public JsonSchema string(String name, String description) {
        properties.put(name, Map.of("type", "string", "description", description));
        return this;
    }

    public JsonSchema integer(String name, String description) {
        properties.put(name, Map.of("type", "integer", "description", description));
        return this;
    }

    public JsonSchema number(String name, String description) {
        properties.put(name, Map.of("type", "number", "description", description));
        return this;
    }

    public JsonSchema bool(String name, String description) {
        properties.put(name, Map.of("type", "boolean", "description", description));
        return this;
    }

    public JsonSchema required(String... names) {
        required.addAll(List.of(names));
        return this;
    }

    public Map<String, Object> build() {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", required);
    }
}
