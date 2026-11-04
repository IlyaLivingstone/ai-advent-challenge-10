package com.ilyalivingstone.aiadvent.day03;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnswerEvaluatorTest {

    private final AnswerEvaluator evaluator = new AnswerEvaluator();
    private final ReasoningTaskCatalog catalog = new ReasoningTaskCatalog();

    @Test
    void extractsFinalAnswerAndAcceptsUnitForNumericTask() {
        ReasoningTask task = task("binary");
        Evaluation result = evaluator.evaluate("Получилось 111659 строк.\nFINAL: 111659 строк", task);

        assertThat(result.finalAnswer()).isEqualTo("111659 строк");
        assertThat(result.correct()).isTrue();
    }

    @Test
    void comparesFractionAsText() {
        Evaluation result = evaluator.evaluate("Вероятность равна 7/22.\nFINAL: 7/22", task("probability"));

        assertThat(result.correct()).isTrue();
    }

    @Test
    void comparesTextAnswerCaseInsensitively() {
        Evaluation result = evaluator.evaluate("Последний — D.\n**FINAL:** d", task("queue"));

        assertThat(result.finalAnswer()).isEqualTo("d");
        assertThat(result.correct()).isTrue();
    }

    @Test
    void rejectsWrongAnswer() {
        Evaluation result = evaluator.evaluate("Ответ неверный\nFINAL: 111658", task("binary"));

        assertThat(result.correct()).isFalse();
    }

    @Test
    void rejectsMissingFinalLine() {
        Evaluation result = evaluator.evaluate("Получилось 111659", task("binary"));

        assertThat(result.correct()).isFalse();
        assertThat(result.note()).contains("FINAL");
    }

    private ReasoningTask task(String id) {
        return catalog.tasks().stream()
                .filter(task -> task.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
