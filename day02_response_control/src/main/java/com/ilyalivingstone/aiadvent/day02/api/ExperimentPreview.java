package com.ilyalivingstone.aiadvent.day02.api;

import java.util.List;

public record ExperimentPreview(
        String prompt,
        List<PreviewVariant> variants
) {

    public record PreviewVariant(
            String id,
            String title,
            int maxTokens,
            List<String> stopSequences,
            boolean formatInstruction,
            boolean jsonMode,
            boolean schemaInstruction,
            String prompt
    ) {
    }
}
