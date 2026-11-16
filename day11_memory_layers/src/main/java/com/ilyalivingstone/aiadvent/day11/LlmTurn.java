package com.ilyalivingstone.aiadvent.day11;

public record LlmTurn(
        LlmReply reply,
        String requestJson,
        String responseJson,
        TokenUsage usage
) {
}
