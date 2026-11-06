package com.ilyalivingstone.aiadvent.day05;

import java.util.List;

public record ModelMeta(
        int taskCount,
        int totalRequests,
        List<TaskMeta> tasks,
        List<ModelMetaItem> models
) {
    public record TaskMeta(String id, String title, String category) {
    }

    public record ModelMetaItem(
            String id,
            String title,
            String role,
            String model,
            int timeoutSeconds,
            double inputPricePerMillion,
            double outputPricePerMillion
    ) {
    }
}
