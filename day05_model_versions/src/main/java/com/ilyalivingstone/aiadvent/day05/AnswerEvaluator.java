package com.ilyalivingstone.aiadvent.day05;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AnswerEvaluator {

    private static final Pattern FINAL_LINE = Pattern.compile(
            "(?im)^\\s*\\**FINAL\\**\\s*:\\s*(.+?)\\s*$"
    );
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:[.,]\\d+)?");

    public Evaluation evaluate(String rawContent, ReasoningTask task) {
        if (rawContent == null || rawContent.isBlank()) {
            return new Evaluation("", false, "Ответ пустой");
        }

        Matcher matcher = FINAL_LINE.matcher(rawContent);
        String finalAnswer = null;
        while (matcher.find()) {
            finalAnswer = matcher.group(1);
        }
        if (finalAnswer == null) {
            return new Evaluation("", false, "Строка FINAL: не найдена");
        }

        finalAnswer = finalAnswer
                .replace("*", "")
                .replace("`", "")
                .trim()
                .replaceAll("[.!;]+$", "")
                .trim();
        if (finalAnswer.isBlank()) {
            return new Evaluation("", false, "Финальный ответ пустой");
        }

        String answer = finalAnswer;
        boolean correct;
        if (task.numeric()) {
            correct = numericMatch(answer, task.expectedAnswer());
        } else {
            String normalizedAnswer = normalizeText(answer);
            correct = task.acceptedAnswers().stream()
                    .map(this::normalizeText)
                    .anyMatch(normalizedAnswer::equals);
        }

        return new Evaluation(answer, correct, correct ? "Ответ совпал" : "Ответ не совпал с эталоном");
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

    private String normalizeText(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
