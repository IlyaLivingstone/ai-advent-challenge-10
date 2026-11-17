package com.ilyalivingstone.aiadvent.day12;

public record LlmTurn(
        LlmReply reply,
        String requestJson,
        String responseJson,
        TokenUsage usage
) {
}
