package com.ilyalivingstone.aiadvent.day03;

public record ReasoningRun(
        String taskId,
        String taskTitle,
        String category,
        String expectedAnswer,
        String finalAnswer,
        boolean correct,
        String evaluationNote,
        String rawContent,
        String generatedPrompt,
        int promptTokens,
        int completionTokens,
        int reasoningTokens,
        long latencyMs,
        int apiRequests,
        String finishReason,
        String error
) {
}
