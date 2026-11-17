package com.ilyalivingstone.aiadvent.day12;

import java.util.List;

public record LlmReply(String content, String reasoning, List<ToolCall> toolCalls) {
    public LlmReply {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
