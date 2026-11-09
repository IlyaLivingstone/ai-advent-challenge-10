package com.ilyalivingstone.aiadvent.day06;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class ToolRegistry {

    private static final Map<String, Double> RATES = Map.of(
            "RUB", 1.0,
            "USD", 92.5,
            "EUR", 100.0
    );

    private final List<ToolDefinition> definitions = List.of(
            new ToolDefinition(
                    "current_time",
                    "Текущее время в указанном часовом поясе",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "timezone", Map.of("type", "string", "description", "IANA timezone, например Europe/Moscow")
                            ),
                            "required", List.of("timezone")
                    )
            ),
            new ToolDefinition(
                    "calculate",
                    "Безопасно вычисляет арифметическое выражение: + - * / и скобки",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "expression", Map.of("type", "string", "description", "Арифметическое выражение")
                            ),
                            "required", List.of("expression")
                    )
            ),
            new ToolDefinition(
                    "convert_currency",
                    "Переводит сумму по фиксированному курсу RUB/USD/EUR",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "amount", Map.of("type", "number"),
                                    "from", Map.of("type", "string", "enum", List.of("RUB", "USD", "EUR")),
                                    "to", Map.of("type", "string", "enum", List.of("RUB", "USD", "EUR"))
                            ),
                            "required", List.of("amount", "from", "to")
                    )
            )
    );

    public List<ToolDefinition> definitions() {
        return definitions;
    }

    public Map<String, Object> execute(String name, JsonNode arguments) {
        return switch (name) {
            case "current_time" -> currentTime(arguments);
            case "calculate" -> calculate(arguments);
            case "convert_currency" -> convertCurrency(arguments);
            default -> throw new IllegalArgumentException("Неизвестный инструмент: " + name);
        };
    }

    private Map<String, Object> currentTime(JsonNode args) {
        String timezone = args.path("timezone").asText("Europe/Moscow");
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime now = ZonedDateTime.now(zone);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timezone", zone.getId());
        result.put("time", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")));
        result.put("iso", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return result;
    }

    private Map<String, Object> calculate(JsonNode args) {
        String expression = args.path("expression").asText("");
        double value = new ExpressionParser(expression).parse();
        return Map.of("expression", expression, "result", value);
    }

    private Map<String, Object> convertCurrency(JsonNode args) {
        double amount = args.path("amount").asDouble(Double.NaN);
        String from = args.path("from").asText("").toUpperCase();
        String to = args.path("to").asText("").toUpperCase();
        if (Double.isNaN(amount) || !RATES.containsKey(from) || !RATES.containsKey(to)) {
            throw new IllegalArgumentException("Ожидаются amount, from и to из RUB/USD/EUR");
        }
        double converted = amount * RATES.get(from) / RATES.get(to);
        double rounded = BigDecimal.valueOf(converted).setScale(2, RoundingMode.HALF_UP).doubleValue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("amount", amount);
        result.put("from", from);
        result.put("to", to);
        result.put("rate", RATES.get(from) / RATES.get(to));
        result.put("converted", rounded);
        result.put("note", "фиксированный демонстрационный курс");
        return result;
    }

    private static final class ExpressionParser {
        private final String input;
        private int position;

        private ExpressionParser(String input) {
            this.input = input;
        }

        double parse() {
            if (input == null || input.isBlank()) {
                throw new IllegalArgumentException("Пустое выражение");
            }
            double value = expression();
            skipSpaces();
            if (position != input.length()) {
                throw new IllegalArgumentException("Некорректное выражение");
            }
            return BigDecimal.valueOf(value).setScale(10, RoundingMode.HALF_UP).doubleValue();
        }

        private double expression() {
            double value = term();
            while (true) {
                skipSpaces();
                if (take('+')) value += term();
                else if (take('-')) value -= term();
                else return value;
            }
        }

        private double term() {
            double value = factor();
            while (true) {
                skipSpaces();
                if (take('*')) value *= factor();
                else if (take('/')) {
                    double divisor = factor();
                    if (divisor == 0) throw new IllegalArgumentException("Деление на ноль");
                    value /= divisor;
                } else return value;
            }
        }

        private double factor() {
            skipSpaces();
            if (take('-')) return -factor();
            if (take('+')) return factor();
            if (take('(')) {
                double value = expression();
                skipSpaces();
                if (!take(')')) throw new IllegalArgumentException("Не закрыта скобка");
                return value;
            }
            int start = position;
            while (position < input.length() && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '.')) {
                position++;
            }
            if (start == position) throw new IllegalArgumentException("Ожидалось число");
            return Double.parseDouble(input.substring(start, position));
        }

        private boolean take(char expected) {
            skipSpaces();
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipSpaces() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++;
        }
    }
}
