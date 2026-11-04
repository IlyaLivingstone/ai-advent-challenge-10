package com.ilyalivingstone.aiadvent.day03;

import java.util.List;

public record ReasoningMeta(
        String model,
        int totalRequests,
        List<TaskMeta> tasks,
        List<MethodMeta> methods,
        List<ModeMeta> modes
) {

    public record TaskMeta(
            String id,
            String category,
            String title,
            String question,
            String expectedAnswer
    ) {
    }

    public record MethodMeta(String id, String title) {
    }

    public record ModeMeta(String id, String title) {
    }
}
