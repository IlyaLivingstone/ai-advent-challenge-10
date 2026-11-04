package com.ilyalivingstone.aiadvent.day03;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReasoningTaskCatalogTest {

    @Test
    void containsSixUniqueTasksWithExpectedAnswers() {
        ReasoningTaskCatalog catalog = new ReasoningTaskCatalog();

        assertThat(catalog.tasks()).hasSize(7);
        assertThat(catalog.tasks())
                .extracting(ReasoningTask::id)
                .doesNotHaveDuplicates();
        assertThat(catalog.tasks())
                .allSatisfy(task -> {
                    assertThat(task.question()).isNotBlank();
                    assertThat(task.expectedAnswer()).isNotBlank();
                    assertThat(task.acceptedAnswers()).isNotEmpty();
                });
    }
}
