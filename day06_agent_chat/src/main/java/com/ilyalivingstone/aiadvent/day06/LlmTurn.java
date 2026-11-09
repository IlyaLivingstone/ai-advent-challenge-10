package com.ilyalivingstone.aiadvent.day06;

public record LlmTurn(
        LlmReply reply,
        String requestJson,
        String responseJson
) {
}
