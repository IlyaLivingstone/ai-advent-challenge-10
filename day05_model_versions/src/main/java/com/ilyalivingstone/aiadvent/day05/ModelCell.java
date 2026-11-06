package com.ilyalivingstone.aiadvent.day05;

import java.util.List;

public record ModelCell(
        String modelId,
        String modelTitle,
        String role,
        int taskCount,
        int correctCount,
        int formatValidCount,
        int totalTokens,
        long totalLatencyMs,
        double totalCostUsd,
        double avgLatencyMs,
        List<ModelRun> runs,
        String summary
) {
    public ModelCell {
        runs = runs == null ? List.of() : List.copyOf(runs);
    }
}
