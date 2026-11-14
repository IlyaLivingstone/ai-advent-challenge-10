package com.ilyalivingstone.aiadvent.day09;

public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
    public static TokenUsage empty() {
        return new TokenUsage(0, 0, 0);
    }
}
