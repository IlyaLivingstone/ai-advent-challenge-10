package com.ilyalivingstone.aiadvent.day04;

import java.util.List;

public record TemperatureCell(
        String taskId,
        String taskTitle,
        PromptKind kind,
        String temperatureId,
        String temperatureLabel,
        double temperature,
        int runCount,
        int formatValidCount,
        Integer correctCount,
        int uniqueAnswers,
        int maxIdenticalCount,
        double averageSimilarity,
        double diversity,
        int totalTokens,
        long latencyMs,
        List<TemperatureRun> runs,
        String summary
) {

    public TemperatureCell {
        runs = runs == null ? List.of() : List.copyOf(runs);
    }
}
