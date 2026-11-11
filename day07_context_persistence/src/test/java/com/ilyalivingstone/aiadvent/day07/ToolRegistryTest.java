package com.ilyalivingstone.aiadvent.day07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ToolRegistryTest {

    private final ToolRegistry tools = new ToolRegistry();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void calculatorRespectsPrecedence() {
        Map<String, Object> result = tools.execute("calculate", jsonMapper.readTree("{\"expression\":\"12 + 3 * 4\"}"));
        assertThat(result.get("result")).isEqualTo(24.0);
    }

    @Test
    void calculatorSupportsParentheses() {
        Map<String, Object> result = tools.execute("calculate", jsonMapper.readTree("{\"expression\":\"(12 + 3) * 4\"}"));
        assertThat(result.get("result")).isEqualTo(60.0);
    }

    @Test
    void calculatorRejectsDivisionByZero() {
        assertThatThrownBy(() -> tools.execute("calculate", jsonMapper.readTree("{\"expression\":\"10 / 0\"}")))
                .hasMessageContaining("Деление на ноль");
    }

    @Test
    void convertsCurrency() {
        Map<String, Object> result = tools.execute("convert_currency", jsonMapper.readTree("{\"amount\":925,\"from\":\"RUB\",\"to\":\"USD\"}"));
        assertThat(result.get("converted")).isEqualTo(10.0);
    }

    @Test
    void returnsMoscowTime() {
        Map<String, Object> result = tools.execute("current_time", jsonMapper.readTree("{\"timezone\":\"Europe/Moscow\"}"));
        assertThat(result.get("timezone")).isEqualTo("Europe/Moscow");
        assertThat(String.valueOf(result.get("time"))).contains("MSK");
    }

    @Test
    void toolSchemasContainThreeTools() {
        assertThat(tools.definitions()).hasSize(3);
    }
}
