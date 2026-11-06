package com.ilyalivingstone.aiadvent.day04;

public record TemperatureTask(
        String id,
        PromptKind kind,
        String title,
        String prompt,
        String expectedAnswer,
        boolean numeric
) {
}
