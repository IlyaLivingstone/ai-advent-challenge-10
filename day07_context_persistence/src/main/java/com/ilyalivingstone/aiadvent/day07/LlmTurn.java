package com.ilyalivingstone.aiadvent.day07;

public record LlmTurn(
        LlmReply reply,
        String requestJson,
        String responseJson
) {
}
