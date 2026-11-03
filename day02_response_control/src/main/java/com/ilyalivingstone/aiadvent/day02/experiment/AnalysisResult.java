package com.ilyalivingstone.aiadvent.day02.experiment;

import java.util.List;

public record AnalysisResult(
        boolean directJson,
        boolean normalizedJson,
        boolean schemaValid,
        String normalizedContent,
        String parseError,
        List<String> schemaErrors
) {

    public AnalysisResult {
        normalizedContent = normalizedContent == null ? "" : normalizedContent;
        schemaErrors = schemaErrors == null ? List.of() : List.copyOf(schemaErrors);
    }

    public static AnalysisResult empty(String parseError) {
        return new AnalysisResult(false, false, false, "", parseError, List.of());
    }
}
