package com.ilyalivingstone.aiadvent.day05;

import java.time.Instant;
import java.util.List;

public record ModelReport(
        String id,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        int taskCount,
        List<ModelCell> cells
) {
    public ModelReport {
        cells = cells == null ? List.of() : List.copyOf(cells);
    }
}
