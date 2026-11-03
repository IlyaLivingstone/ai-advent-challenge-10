package com.ilyalivingstone.aiadvent.day02.experiment;

import java.util.List;

public record VariantSpec(
        ExperimentVariant variant,
        String prompt,
        int maxTokens,
        List<String> stopSequences,
        boolean formatInstruction,
        boolean jsonMode,
        boolean schemaInstruction
) {

    public VariantSpec {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens должен быть положительным");
        }
        stopSequences = stopSequences == null ? List.of() : List.copyOf(stopSequences);
    }
}
