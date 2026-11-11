package com.ilyalivingstone.aiadvent.day08;

public record LlmTurn(
        LlmReply reply,
        String requestJson,
        String responseJson,
        TokenUsage usage
) {
}
