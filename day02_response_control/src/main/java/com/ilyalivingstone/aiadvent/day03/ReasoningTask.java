package com.ilyalivingstone.aiadvent.day03;

import java.util.List;

public record ReasoningTask(
        String id,
        String category,
        String title,
        String question,
        String expectedAnswer,
        List<String> acceptedAnswers,
        boolean numeric
) {

    public ReasoningTask {
        acceptedAnswers = acceptedAnswers == null ? List.of() : List.copyOf(acceptedAnswers);
    }
}
