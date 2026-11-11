package com.ilyalivingstone.aiadvent.day07;

import java.util.Map;

public record ToolDefinition(String name, String description, Map<String, Object> parameters) {

    public Map<String, Object> toOpenAi() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters
                )
        );
    }
}
