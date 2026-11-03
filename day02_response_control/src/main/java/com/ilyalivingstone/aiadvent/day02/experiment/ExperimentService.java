package com.ilyalivingstone.aiadvent.day02.experiment;

import com.ilyalivingstone.aiadvent.day02.llm.ChatCompletionResponse;
import com.ilyalivingstone.aiadvent.day02.llm.DeepSeekClient;
import com.ilyalivingstone.aiadvent.day02.llm.DeepSeekProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class ExperimentService {

    public static final int MAX_REPEATS = 10;

    private final DeepSeekClient client;
    private final DeepSeekProperties properties;
    private final ExperimentCatalog catalog;
    private final JsonResponseAnalyzer analyzer;
    private final ConcurrentMap<String, ExperimentReport> reports = new ConcurrentHashMap<>();

    public ExperimentService(
            DeepSeekClient client,
            DeepSeekProperties properties,
            ExperimentCatalog catalog,
            JsonResponseAnalyzer analyzer
    ) {
        this.client = client;
        this.properties = properties;
        this.catalog = catalog;
        this.analyzer = analyzer;
    }

    public ExperimentReport run(int repeats) {
        return run(repeats, null);
    }

    public ExperimentReport run(int repeats, String basePrompt) {
        if (repeats < 1 || repeats > MAX_REPEATS) {
            throw new IllegalArgumentException("Количество повторов должно быть от 1 до " + MAX_REPEATS);
        }
        if (!properties.hasApiKey()) {
            throw new IllegalStateException("DEEPSEEK_API_KEY не задан");
        }

        String normalizedPrompt = catalog.normalizePrompt(basePrompt);
        Instant startedAt = Instant.now();
        List<VariantResult> variantResults = new ArrayList<>();
        for (VariantSpec spec : catalog.variantsFor(normalizedPrompt)) {
            List<RunResult> runs = new ArrayList<>();
            for (int runNumber = 1; runNumber <= repeats; runNumber++) {
                runs.add(runOnce(spec, runNumber));
            }
            variantResults.add(new VariantResult(spec, runs, summarize(spec, runs)));
        }
        Instant finishedAt = Instant.now();

        int totalRequests = variantResults.stream()
                .mapToInt(result -> result.runs().size())
                .sum();
        ExperimentReport report = new ExperimentReport(
                UUID.randomUUID().toString(),
                normalizedPrompt,
                startedAt,
                finishedAt,
                repeats,
                totalRequests,
                finishedAt.toEpochMilli() - startedAt.toEpochMilli(),
                variantResults
        );
        reports.put(report.id(), report);
        return report;
    }

    public Optional<ExperimentReport> find(String id) {
        return Optional.ofNullable(reports.get(id));
    }

    private RunResult runOnce(VariantSpec spec, int runNumber) {
        long started = System.nanoTime();
        try {
            ChatCompletionResponse response = client.complete(
                    spec.prompt(),
                    spec.maxTokens(),
                    spec.stopSequences(),
                    spec.jsonMode()
            );
            ChatCompletionResponse.Choice choice = response.firstChoice();
            String content = choice.message() == null ? "" : choice.message().content();
            ChatCompletionResponse.Usage usage = response.usage();
            AnalysisResult analysis = analyzer.analyze(content);

            return new RunResult(
                    runNumber,
                    elapsedMs(started),
                    content,
                    choice.finishReason(),
                    usage == null ? 0 : usage.promptTokens(),
                    usage == null ? 0 : usage.completionTokens(),
                    usage == null ? 0 : usage.reasoningTokens(),
                    analysis,
                    null
            );
        } catch (RuntimeException error) {
            return new RunResult(
                    runNumber,
                    elapsedMs(started),
                    "",
                    null,
                    0,
                    0,
                    0,
                    AnalysisResult.empty("Запрос не выполнен"),
                    error.getMessage()
            );
        }
    }

    private VariantSummary summarize(VariantSpec spec, List<RunResult> runs) {
        int apiErrors = (int) runs.stream().filter(run -> !run.succeeded()).count();
        int directJson = (int) runs.stream().filter(run -> run.analysis().directJson()).count();
        int normalizedJson = (int) runs.stream().filter(run -> run.analysis().normalizedJson()).count();
        int schemaValid = (int) runs.stream().filter(run -> run.analysis().schemaValid()).count();
        long averageLatencyMs = Math.round(runs.stream().mapToLong(RunResult::latencyMs).average().orElse(0));
        int averageCompletionTokens = (int) Math.round(
                runs.stream().mapToInt(RunResult::completionTokens).average().orElse(0)
        );

        Map<String, Integer> finishReasons = new LinkedHashMap<>();
        for (RunResult run : runs) {
            String reason = run.finishReason() == null ? "нет ответа" : run.finishReason();
            finishReasons.merge(reason, 1, Integer::sum);
        }

        List<String> findings = new ArrayList<>();
        if (apiErrors > 0) {
            findings.add("Ошибок API: " + apiErrors + " из " + runs.size());
        }
        if (directJson < runs.size()) {
            findings.add("Прямой JSON-парсинг: " + directJson + " из " + runs.size());
        }
        if (normalizedJson > directJson) {
            findings.add("Markdown/текст вокруг JSON: " + (normalizedJson - directJson));
        }
        if (schemaValid < normalizedJson) {
            findings.add("Нарушений JSON Schema: " + (normalizedJson - schemaValid));
        }
        if (finishReasons.containsKey("length")) {
            findings.add("Ответ оборван по max_tokens: " + finishReasons.get("length"));
        }
        if (findings.isEmpty()) {
            findings.add("Все ответы разобраны и соответствуют схеме");
        }

        return new VariantSummary(
                runs.size(),
                apiErrors,
                directJson,
                normalizedJson,
                schemaValid,
                averageLatencyMs,
                averageCompletionTokens,
                finishReasons,
                findings
        );
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
