package com.ilyalivingstone.aiadvent.day02.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JsonResponseAnalyzerTest {

    private final JsonResponseAnalyzer analyzer = new JsonResponseAnalyzer(JsonMapper.builder().build());

    @Test
    void acceptsPlainJsonMatchingSchema() {
        AnalysisResult result = analyzer.analyze(validJson());

        assertThat(result.directJson()).isTrue();
        assertThat(result.normalizedJson()).isTrue();
        assertThat(result.schemaValid()).isTrue();
        assertThat(result.schemaErrors()).isEmpty();
    }

    @Test
    void extractsJsonFromMarkdownFence() {
        AnalysisResult result = analyzer.analyze("Вот результат:\n```json\n" + validJson() + "\n```");

        assertThat(result.directJson()).isFalse();
        assertThat(result.normalizedJson()).isTrue();
        assertThat(result.schemaValid()).isTrue();
    }

    @Test
    void reportsSchemaViolationForMissingFeature() {
        AnalysisResult result = analyzer.analyze("""
                {
                  "name": "ФинБаланс",
                  "audience": ["молодые специалисты"],
                  "features": ["Бюджеты", "Категории"],
                  "risk": "Конкуренция"
                }
                """);

        assertThat(result.directJson()).isTrue();
        assertThat(result.normalizedJson()).isTrue();
        assertThat(result.schemaValid()).isFalse();
        assertThat(result.schemaErrors()).isNotEmpty();
    }

    @Test
    void rejectsNonJsonResponse() {
        AnalysisResult result = analyzer.analyze("Это обычный текст без JSON-объекта");

        assertThat(result.directJson()).isFalse();
        assertThat(result.normalizedJson()).isFalse();
        assertThat(result.schemaValid()).isFalse();
        assertThat(result.parseError()).isNotBlank();
    }

    @Test
    void rejectsEmptyResponse() {
        AnalysisResult result = analyzer.analyze("  ");

        assertThat(result.directJson()).isFalse();
        assertThat(result.normalizedJson()).isFalse();
        assertThat(result.schemaValid()).isFalse();
        assertThat(result.parseError()).isEqualTo("Ответ пустой");
    }

    private String validJson() {
        return """
                {
                  "name": "ФинБаланс",
                  "audience": ["молодые специалисты", "семьи"],
                  "features": ["Бюджеты", "Категории расходов", "Напоминания"],
                  "risk": "Высокая конкуренция на рынке"
                }
                """;
    }
}
