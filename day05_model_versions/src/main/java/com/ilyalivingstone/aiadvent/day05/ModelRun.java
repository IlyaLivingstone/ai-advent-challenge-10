package com.ilyalivingstone.aiadvent.day05;

public record ModelRun(
        String modelId,
        String taskId,
        String taskTitle,
        String category,
        String expectedAnswer,
        String finalAnswer,
        boolean formatValid,
        boolean correct,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs,
        double costUsd,
        String rawContent,
        String error
) {
}
