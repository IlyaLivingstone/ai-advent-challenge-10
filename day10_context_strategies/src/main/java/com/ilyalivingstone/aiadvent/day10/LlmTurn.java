package com.ilyalivingstone.aiadvent.day10;

public record LlmTurn(
        LlmReply reply,
        String requestJson,
        String responseJson,
        TokenUsage usage
) {
}
