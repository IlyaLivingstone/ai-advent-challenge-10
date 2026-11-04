package com.ilyalivingstone.aiadvent.day03;

import java.time.Instant;
import java.util.List;

public record ReasoningReport(
        String id,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        int taskCount,
        List<ReasoningCell> cells
) {

    public ReasoningReport {
        cells = cells == null ? List.of() : List.copyOf(cells);
    }
}
