package com.ilyalivingstone.aiadvent.day02.experiment;

public record RunResult(
        int runNumber,
        long latencyMs,
        String content,
        String finishReason,
        int promptTokens,
        int completionTokens,
        int reasoningTokens,
        AnalysisResult analysis,
        String error
) {

    public RunResult {
        content = content == null ? "" : content;
        analysis = analysis == null ? AnalysisResult.empty("Анализ не выполнялся") : analysis;
    }

    public boolean succeeded() {
        return error == null;
    }
}
