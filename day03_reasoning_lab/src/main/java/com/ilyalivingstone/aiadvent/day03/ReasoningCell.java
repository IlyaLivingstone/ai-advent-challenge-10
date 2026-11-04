package com.ilyalivingstone.aiadvent.day03;

import java.util.List;

public record ReasoningCell(
        ReasoningMethod method,
        ReasoningMode mode,
        int taskCount,
        int correctCount,
        int promptTokens,
        int completionTokens,
        int reasoningTokens,
        int totalTokens,
        long latencyMs,
        int apiRequests,
        List<ReasoningRun> runs,
        String summary
) {

    public ReasoningCell {
        runs = runs == null ? List.of() : List.copyOf(runs);
    }
}
