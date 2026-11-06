package com.ilyalivingstone.aiadvent.day04;

import java.time.Instant;
import java.util.List;

public record TemperatureReport(
        String id,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        int runsPerCombination,
        List<TemperatureCell> cells
) {

    public TemperatureReport {
        cells = cells == null ? List.of() : List.copyOf(cells);
    }
}
