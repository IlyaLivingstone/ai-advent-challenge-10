package com.ilyalivingstone.aiadvent.day04;

public record TemperatureRun(
        String taskId,
        String taskTitle,
        PromptKind kind,
        String temperatureId,
        double temperature,
        int runNumber,
        String rawContent,
        String parsedAnswer,
        boolean formatValid,
        Boolean correct,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs,
        String error
) {
}
