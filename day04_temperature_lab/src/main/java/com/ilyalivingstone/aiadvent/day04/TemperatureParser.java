package com.ilyalivingstone.aiadvent.day04;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TemperatureParser {

    private static final Pattern FINAL_LINE = Pattern.compile(
            "(?im)^\\s*\\**FINAL\\**\\s*:\\s*(.+?)\\s*$"
    );
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:[.,]\\d+)?");

    private final JsonMapper jsonMapper;

    public TemperatureParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public TemperatureEvaluation evaluate(TemperatureTask task, String rawContent) {
        if (task.kind() == PromptKind.CREATIVE) {
            return parseCreative(rawContent);
        }
        return parseExact(task, rawContent);
    }

    private TemperatureEvaluation parseExact(TemperatureTask task, String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return new TemperatureEvaluation("", false, false, List.of());
        }
        Matcher matcher = FINAL_LINE.matcher(rawContent);
        String finalAnswer = null;
        while (matcher.find()) {
            finalAnswer = matcher.group(1);
        }
        if (finalAnswer == null) {
            return new TemperatureEvaluation("", false, false, List.of());
        }
        String answer = finalAnswer.replace("*", "").replace("`", "").trim().replaceAll("[.!;]+$", "").trim();
        if (answer.isBlank()) {
            return new TemperatureEvaluation("", false, false, List.of());
        }
        Matcher number = NUMBER.matcher(answer);
        String canonicalAnswer = number.find() ? number.group().replace(',', '.') : answer;
        boolean correct = numericMatch(answer, task.expectedAnswer());
        return new TemperatureEvaluation(canonicalAnswer, true, correct, List.of());
    }

    private TemperatureEvaluation parseCreative(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return new TemperatureEvaluation("", false, false, List.of());
        }
        String text = rawContent.trim();
        if (text.startsWith("```")) {
            return new TemperatureEvaluation(text, false, false, List.of());
        }
        try {
            JsonNode node = jsonMapper.readTree(text);
            if (!node.isArray() || node.size() != 5) {
                return new TemperatureEvaluation(text, false, false, List.of());
            }
            List<String> names = new ArrayList<>();
            for (JsonNode item : node) {
                if (!item.isTextual() || item.asString().isBlank()) {
                    return new TemperatureEvaluation(text, false, false, List.of());
                }
                names.add(item.asString().strip());
            }
            return new TemperatureEvaluation(String.join(" | ", names), true, null, List.copyOf(names));
        } catch (RuntimeException error) {
            return new TemperatureEvaluation(text, false, false, List.of());
        }
    }

    private boolean numericMatch(String actualText, String expectedText) {
        Matcher actualMatcher = NUMBER.matcher(actualText);
        if (!actualMatcher.find()) {
            return false;
        }
        BigDecimal actual = new BigDecimal(actualMatcher.group().replace(',', '.'));
        BigDecimal expected = new BigDecimal(expectedText.replace(',', '.'));
        return actual.compareTo(expected) == 0;
    }
}
