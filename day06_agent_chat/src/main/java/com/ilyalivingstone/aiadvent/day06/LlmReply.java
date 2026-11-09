package com.ilyalivingstone.aiadvent.day06;

import java.util.List;

public record LlmReply(String content, List<ToolCall> toolCalls) {
    public LlmReply {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
