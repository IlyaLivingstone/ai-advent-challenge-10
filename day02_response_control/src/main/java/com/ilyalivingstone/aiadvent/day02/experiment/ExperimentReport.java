package com.ilyalivingstone.aiadvent.day02.experiment;

import java.time.Instant;
import java.util.List;

public record ExperimentReport(
        String id,
        String basePrompt,
        Instant startedAt,
        Instant finishedAt,
        int repeats,
        int totalRequests,
        long durationMs,
        List<VariantResult> variants
) {

    public ExperimentReport {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }
}
