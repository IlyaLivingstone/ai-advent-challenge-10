package com.ilyalivingstone.aiadvent.day05.llm;

public record ChatMessage(String role, String content) {

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }
}
