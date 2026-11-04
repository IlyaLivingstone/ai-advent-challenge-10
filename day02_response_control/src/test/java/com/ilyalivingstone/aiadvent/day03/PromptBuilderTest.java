package com.ilyalivingstone.aiadvent.day03;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();
    private final ReasoningTask task = new ReasoningTaskCatalog().tasks().getFirst();

    @Test
    void directPromptHasNoSolutionHint() {
        String prompt = builder.promptFor(ReasoningMethod.DIRECT, task);

        assertThat(prompt).contains(task.question());
        assertThat(prompt).contains("FINAL:");
        assertThat(prompt).doesNotContain("пошагово");
    }

    @Test
    void stepByStepPromptContainsInstruction() {
        String prompt = builder.promptFor(ReasoningMethod.STEP_BY_STEP, task);

        assertThat(prompt).contains("Решай задачу пошагово");
        assertThat(prompt).contains("FINAL:");
    }

    @Test
    void metaPromptAsksToBuildPromptWithoutSolving() {
        String prompt = builder.metaPromptStage(task);

        assertThat(prompt).contains("Составь подробный промпт");
        assertThat(prompt).contains("Не решай задачу");
        assertThat(prompt).contains(task.question());
    }

    @Test
    void generatedPromptIsWrappedWithTaskAndFinalContract() {
        String prompt = builder.fromGeneratedPrompt("Реши задачу через уравнение", task);

        assertThat(prompt).contains("Реши задачу через уравнение");
        assertThat(prompt).contains(task.question());
        assertThat(prompt).contains("FINAL:");
    }

    @Test
    void expertPromptDefinesThreeRoles() {
        String prompt = builder.promptFor(ReasoningMethod.EXPERT_PANEL, task);

        assertThat(prompt).contains("аналитик", "инженер", "критик");
        assertThat(prompt).contains("FINAL:");
    }
}
