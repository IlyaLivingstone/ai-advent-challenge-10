package com.ilyalivingstone.aiadvent.day02.experiment;

import java.util.List;

public record VariantResult(
        VariantSpec spec,
        List<RunResult> runs,
        VariantSummary summary
) {

    public VariantResult {
        runs = runs == null ? List.of() : List.copyOf(runs);
    }
}
