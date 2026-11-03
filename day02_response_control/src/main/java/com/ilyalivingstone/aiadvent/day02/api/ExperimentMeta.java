package com.ilyalivingstone.aiadvent.day02.api;

import java.util.List;

public record ExperimentMeta(
        String prompt,
        int defaultRepeats,
        int maxRepeats,
        int maxPromptLength,
        List<VariantMeta> variants
) {

    public record VariantMeta(
            String id,
            String title,
            int maxTokens,
            List<String> stopSequences,
            boolean formatInstruction,
            boolean jsonMode,
            boolean schemaInstruction
    ) {
    }
}
