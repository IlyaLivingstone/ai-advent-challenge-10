package com.ilyalivingstone.aiadvent.day04;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TemperatureParserTest {

    private final TemperatureParser parser = new TemperatureParser(JsonMapper.builder().build());
    private final TemperatureTaskCatalog catalog = new TemperatureTaskCatalog();

    @Test
    void acceptsCorrectFinalAnswer() {
        TemperatureEvaluation result = parser.evaluate(task("library"), "30 + 70 + 100 = 200\nFINAL: 200");
        assertThat(result.formatValid()).isTrue();
        assertThat(result.correct()).isTrue();
    }

    @Test
    void rejectsWrongAnswer() {
        TemperatureEvaluation result = parser.evaluate(task("library"), "FINAL: 201");
        assertThat(result.formatValid()).isTrue();
        assertThat(result.correct()).isFalse();
    }

    @Test
    void missingFinalIsFormatFailure() {
        TemperatureEvaluation result = parser.evaluate(task("library"), "Ответ: 200");
        assertThat(result.formatValid()).isFalse();
    }

    @Test
    void acceptsCreativeJsonArray() {
        TemperatureEvaluation result = parser.evaluate(task("names"), "[\"Семья\", \"Бюджет\", \"Копилка\", \"План\", \"Кошелёк\"]");
        assertThat(result.formatValid()).isTrue();
        assertThat(result.names()).hasSize(5);
    }

    @Test
    void rejectsBrokenCreativeFormat() {
        TemperatureEvaluation result = parser.evaluate(task("names"), "Вот пять названий: Семья, Бюджет, План");
        assertThat(result.formatValid()).isFalse();
    }

    @Test
    void catalogHasTwoTaskTypesAndThreeTemperatures() {
        assertThat(catalog.tasks()).hasSize(2);
        assertThat(catalog.tasks()).extracting(TemperatureTask::kind)
                .containsExactlyInAnyOrder(PromptKind.EXACT, PromptKind.CREATIVE);
        assertThat(TemperatureLevel.values()).hasSize(3);
    }

    private TemperatureTask task(String id) {
        return catalog.tasks().stream().filter(task -> task.id().equals(id)).findFirst().orElseThrow();
    }
}
