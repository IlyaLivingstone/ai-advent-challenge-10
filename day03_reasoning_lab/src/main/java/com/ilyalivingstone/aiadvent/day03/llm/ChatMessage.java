package com.ilyalivingstone.aiadvent.day03.llm;

public record ChatMessage(String role, String content) {

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }
}
