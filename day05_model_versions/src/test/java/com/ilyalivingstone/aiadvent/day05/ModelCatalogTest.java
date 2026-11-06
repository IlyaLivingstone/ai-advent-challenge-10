package com.ilyalivingstone.aiadvent.day05;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelCatalogTest {

    @Test
    void hasSmallMediumAndFlagshipModels() {
        assertThat(new ModelCatalog().models())
                .extracting(ModelProfile::id)
                .containsExactly("qwen-local", "deepseek-flash", "kimi-pro");
    }

    @Test
    void day3CatalogStillHasSevenTasks() {
        assertThat(new ReasoningTaskCatalog().tasks()).hasSize(7);
    }
}
