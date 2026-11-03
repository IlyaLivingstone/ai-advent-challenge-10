package com.ilyalivingstone.aiadvent.day02.experiment;

import java.util.List;
import java.util.Map;

public record VariantSummary(
        int runs,
        int apiErrors,
        int directJson,
        int normalizedJson,
        int schemaValid,
        long averageLatencyMs,
        int averageCompletionTokens,
        Map<String, Integer> finishReasons,
        List<String> findings
) {

    public VariantSummary {
        finishReasons = finishReasons == null ? Map.of() : Map.copyOf(finishReasons);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
