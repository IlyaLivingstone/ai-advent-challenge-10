package com.ilyalivingstone.aiadvent.day04;

import com.ilyalivingstone.aiadvent.day04.llm.ChatCompletionResponse;
import com.ilyalivingstone.aiadvent.day04.llm.DeepSeekClient;
import com.ilyalivingstone.aiadvent.day04.llm.DeepSeekProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TemperatureExperimentService {

    public static final int RUNS_PER_COMBINATION = 10;

    private final DeepSeekClient client;
    private final DeepSeekProperties properties;
    private final TemperatureTaskCatalog catalog;
    private final TemperatureParser parser;

    public TemperatureExperimentService(
            DeepSeekClient client,
            DeepSeekProperties properties,
            TemperatureTaskCatalog catalog,
            TemperatureParser parser
    ) {
        this.client = client;
        this.properties = properties;
        this.catalog = catalog;
        this.parser = parser;
    }

    public TemperatureReport run() {
        if (!properties.hasApiKey()) {
            throw new IllegalStateException("DEEPSEEK_API_KEY не задан");
        }
        Instant startedAt = Instant.now();
        List<TemperatureCell> cells = new ArrayList<>();
        for (TemperatureTask task : catalog.tasks()) {
            for (TemperatureLevel level : TemperatureLevel.values()) {
                List<TemperatureRun> runs = new ArrayList<>();
                for (int run = 1; run <= RUNS_PER_COMBINATION; run++) {
                    runs.add(runOnce(task, level, run));
                }
                cells.add(toCell(task, level, runs));
            }
        }
        Instant finishedAt = Instant.now();
        return new TemperatureReport(
                UUID.randomUUID().toString(),
                startedAt,
                finishedAt,
                finishedAt.toEpochMilli() - startedAt.toEpochMilli(),
                RUNS_PER_COMBINATION,
                cells
        );
    }

    private TemperatureRun runOnce(TemperatureTask task, TemperatureLevel level, int runNumber) {
        long started = System.nanoTime();
        try {
            ChatCompletionResponse response = client.complete(
                    promptFor(task),
                    task.kind() == PromptKind.EXACT ? 600 : 500,
                    null,
                    false,
                    level.value()
            );
            String content = response.firstChoice().message() == null
                    ? ""
                    : response.firstChoice().message().content();
            TemperatureEvaluation evaluation = parser.evaluate(task, content);
            int promptTokens = response.usage() == null ? 0 : response.usage().promptTokens();
            int completionTokens = response.usage() == null ? 0 : response.usage().completionTokens();
            return new TemperatureRun(
                    task.id(), task.title(), task.kind(), level.id(), level.value(), runNumber,
                    content, evaluation.parsedAnswer(), evaluation.formatValid(), evaluation.correct(),
                    promptTokens, completionTokens, promptTokens + completionTokens,
                    elapsedMs(started), null
            );
        } catch (RuntimeException error) {
            return new TemperatureRun(
                    task.id(), task.title(), task.kind(), level.id(), level.value(), runNumber,
                    "", "", false, task.kind() == PromptKind.EXACT ? false : null,
                    0, 0, 0, elapsedMs(started), error.getMessage()
            );
        }
    }

    private String promptFor(TemperatureTask task) {
        if (task.kind() == PromptKind.CREATIVE) {
            return task.prompt();
        }
        return task.prompt() + "\n\nВ конце ответа добавь ровно одну строку:\nFINAL: <краткий ответ>";
    }

    private TemperatureCell toCell(TemperatureTask task, TemperatureLevel level, List<TemperatureRun> runs) {
        int formatValid = (int) runs.stream().filter(TemperatureRun::formatValid).count();
        Integer correctCount = task.kind() == PromptKind.EXACT
                ? (int) runs.stream().filter(run -> Boolean.TRUE.equals(run.correct())).count()
                : null;
        List<String> answers = runs.stream().map(this::normalizedAnswer).toList();
        int uniqueAnswers = new LinkedHashSet<>(answers).size();
        int maxIdentical = answers.stream()
                .mapToInt(answer -> (int) answers.stream().filter(answer::equals).count())
                .max().orElse(0);
        double averageSimilarity = averagePairwiseSimilarity(answers);
        int totalTokens = runs.stream().mapToInt(TemperatureRun::totalTokens).sum();
        long latency = runs.stream().mapToLong(TemperatureRun::latencyMs).sum();
        String summary;
        if (task.kind() == PromptKind.EXACT) {
            summary = "уникальных %d/%d · верно %d/%d · формат %d/%d".formatted(
                    uniqueAnswers, runs.size(), correctCount == null ? 0 : correctCount, runs.size(), formatValid, runs.size()
            );
        } else {
            summary = "уникальных %d/%d · diversity %.2f · формат %d/%d".formatted(
                    uniqueAnswers, runs.size(), 1.0 - averageSimilarity, formatValid, runs.size()
            );
        }
        return new TemperatureCell(
                task.id(), task.title(), task.kind(), level.id(), level.label(), level.value(),
                runs.size(), formatValid, correctCount, uniqueAnswers, maxIdentical,
                averageSimilarity, 1.0 - averageSimilarity, totalTokens, latency, runs, summary
        );
    }

    private String normalizedAnswer(TemperatureRun run) {
        String value = run.parsedAnswer() == null || run.parsedAnswer().isBlank()
                ? run.rawContent()
                : run.parsedAnswer();
        return normalize(value);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    private double averagePairwiseSimilarity(List<String> answers) {
        if (answers.size() < 2) {
            return 0;
        }
        double total = 0;
        int pairs = 0;
        for (int i = 0; i < answers.size(); i++) {
            for (int j = i + 1; j < answers.size(); j++) {
                total += jaccard(answers.get(i), answers.get(j));
                pairs++;
            }
        }
        return pairs == 0 ? 0 : total / pairs;
    }

    private double jaccard(String left, String right) {
        Set<String> a = words(left);
        Set<String> b = words(right);
        if (a.isEmpty() && b.isEmpty()) {
            return 1;
        }
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private Set<String> words(String value) {
        Set<String> result = new HashSet<>();
        for (String token : value.split("\\s+")) {
            if (!token.isBlank()) {
                result.add(token);
            }
        }
        return result;
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
