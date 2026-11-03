package com.ilyalivingstone.aiadvent.day02.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExperimentCatalogTest {

    private final ExperimentCatalog catalog = new ExperimentCatalog();

    @Test
    void containsAllFiveVariantsWithFixedPrompt() {
        assertThat(catalog.variants()).hasSize(5);
        assertThat(catalog.variants())
                .extracting(spec -> spec.variant().id())
                .containsExactly(
                        "baseline",
                        "explicit_format",
                        "max_tokens",
                        "stop_sequence",
                        "strict_schema"
                );
        assertThat(catalog.variants())
                .allSatisfy(spec -> assertThat(spec.prompt()).startsWith(ExperimentCatalog.DEFAULT_PROMPT));
    }

    @Test
    void buildsEveryVariantForCustomPrompt() {
        String customPrompt = "Разбери идею приложения для медитации";
        assertThat(catalog.variantsFor(customPrompt))
                .hasSize(5)
                .allSatisfy(spec -> assertThat(spec.prompt()).startsWith(customPrompt));
        assertThat(catalog.variantsFor(customPrompt).get(4).prompt())
                .contains("JSON Schema");
    }

    @Test
    void configuresControlVariants() {
        VariantSpec maxTokens = catalog.variants().get(2);
        VariantSpec stopSequence = catalog.variants().get(3);
        VariantSpec strictSchema = catalog.variants().get(4);

        assertThat(maxTokens.maxTokens()).isEqualTo(300);
        assertThat(maxTokens.formatInstruction()).isTrue();
        assertThat(stopSequence.stopSequences()).containsExactly("\n\n");
        assertThat(strictSchema.jsonMode()).isTrue();
        assertThat(strictSchema.schemaInstruction()).isTrue();
        assertThat(strictSchema.prompt()).contains("JSON Schema");
    }
}
