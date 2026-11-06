package com.ilyalivingstone.aiadvent.day04;

import java.util.List;

public record TemperatureEvaluation(
        String parsedAnswer,
        boolean formatValid,
        Boolean correct,
        List<String> names
) {
}
