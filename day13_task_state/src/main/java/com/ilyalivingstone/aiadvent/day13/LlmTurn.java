package com.ilyalivingstone.aiadvent.day13;

public record LlmTurn(
        LlmReply reply,
        String requestJson,
        String responseJson,
        TokenUsage usage
) {
}
