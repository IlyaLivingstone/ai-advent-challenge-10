package com.ilyalivingstone.aiadvent.day03;

import com.ilyalivingstone.aiadvent.day02.llm.ChatCompletionRequest;
import com.ilyalivingstone.aiadvent.day02.llm.ChatCompletionResponse;
import com.ilyalivingstone.aiadvent.day02.llm.DeepSeekClient;
import com.ilyalivingstone.aiadvent.day02.llm.DeepSeekProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReasoningExperimentService {

    private static final int SOLVE_MAX_TOKENS = 16000;
    private static final int META_MAX_TOKENS = 16000;

    private final DeepSeekClient client;
    private final DeepSeekProperties properties;
    private final ReasoningTaskCatalog catalog;
    private final PromptBuilder promptBuilder;
    private final AnswerEvaluator evaluator;

    public ReasoningExperimentService(
            DeepSeekClient client,
            DeepSeekProperties properties,
            ReasoningTaskCatalog catalog,
            PromptBuilder promptBuilder,
            AnswerEvaluator evaluator
    ) {
        this.client = client;
        this.properties = properties;
        this.catalog = catalog;
        this.promptBuilder = promptBuilder;
        this.evaluator = evaluator;
    }

    public ReasoningReport run() {
        if (!properties.hasApiKey()) {
            throw new IllegalStateException("DEEPSEEK_API_KEY не задан");
        }

        Instant startedAt = Instant.now();
        List<ReasoningCell> cells = new ArrayList<>();
        for (ReasoningMethod method : ReasoningMethod.values()) {
            for (ReasoningMode mode : ReasoningMode.values()) {
                List<ReasoningRun> runs = new ArrayList<>();
                for (ReasoningTask task : catalog.tasks()) {
                    runs.add(runTask(method, mode, task));
                }
                cells.add(toCell(method, mode, runs));
            }
        }
        Instant finishedAt = Instant.now();
        return new ReasoningReport(
                UUID.randomUUID().toString(),
                startedAt,
                finishedAt,
                finishedAt.toEpochMilli() - startedAt.toEpochMilli(),
                catalog.tasks().size(),
                cells
        );
    }

    private ReasoningRun runTask(ReasoningMethod method, ReasoningMode mode, ReasoningTask task) {
        long started = System.nanoTime();
        int promptTokens = 0;
        int completionTokens = 0;
        int reasoningTokens = 0;
        int apiRequests = 0;
        String generatedPrompt = null;
        String rawContent = "";
        String finishReason = null;
        String error = null;
        Evaluation evaluation = null;

        try {
            if (method == ReasoningMethod.META_PROMPT) {
                ChatCompletionResponse generation = call(
                        promptBuilder.metaPromptStage(task),
                        META_MAX_TOKENS,
                        mode
                );
                apiRequests++;
                promptTokens += tokens(generation).prompt();
                completionTokens += tokens(generation).completion();
                reasoningTokens += tokens(generation).reasoning();
                generatedPrompt = contentOf(generation).strip();

                ChatCompletionResponse solving = call(
                        promptBuilder.fromGeneratedPrompt(generatedPrompt, task),
                        SOLVE_MAX_TOKENS,
                        mode
                );
                apiRequests++;
                promptTokens += tokens(solving).prompt();
                completionTokens += tokens(solving).completion();
                reasoningTokens += tokens(solving).reasoning();
                rawContent = contentOf(solving);
                finishReason = solving.firstChoice().finishReason();
            } else {
                ChatCompletionResponse response = call(
                        promptBuilder.promptFor(method, task),
                        SOLVE_MAX_TOKENS,
                        mode
                );
                apiRequests++;
                promptTokens = tokens(response).prompt();
                completionTokens = tokens(response).completion();
                reasoningTokens = tokens(response).reasoning();
                rawContent = contentOf(response);
                finishReason = response.firstChoice().finishReason();
            }
            evaluation = evaluator.evaluate(rawContent, task);
        } catch (RuntimeException exception) {
            error = exception.getMessage();
        }

        return new ReasoningRun(
                task.id(),
                task.title(),
                task.category(),
                task.expectedAnswer(),
                evaluation == null ? "" : evaluation.finalAnswer(),
                evaluation != null && evaluation.correct(),
                evaluation == null ? "Запрос не выполнен" : evaluation.note(),
                rawContent,
                generatedPrompt,
                promptTokens,
                completionTokens,
                reasoningTokens,
                elapsedMs(started),
                apiRequests,
                finishReason,
                error
        );
    }

    private ChatCompletionResponse call(String prompt, int maxTokens, ReasoningMode mode) {
        return client.complete(
                prompt,
                maxTokens,
                null,
                false,
                mode.reasoningEffort(),
                mode.thinkingEnabled()
                        ? ChatCompletionRequest.Thinking.enabled()
                        : ChatCompletionRequest.Thinking.disabled()
        );
    }

    private TokenUsage tokens(ChatCompletionResponse response) {
        if (response.usage() == null) {
            return new TokenUsage(0, 0, 0);
        }
        return new TokenUsage(
                response.usage().promptTokens(),
                response.usage().completionTokens(),
                response.usage().reasoningTokens()
        );
    }

    private String contentOf(ChatCompletionResponse response) {
        if (response.firstChoice().message() == null) {
            return "";
        }
        return response.firstChoice().message().content();
    }

    private ReasoningCell toCell(ReasoningMethod method, ReasoningMode mode, List<ReasoningRun> runs) {
        int correct = (int) runs.stream().filter(ReasoningRun::correct).count();
        int promptTokens = runs.stream().mapToInt(ReasoningRun::promptTokens).sum();
        int completionTokens = runs.stream().mapToInt(ReasoningRun::completionTokens).sum();
        int reasoningTokens = runs.stream().mapToInt(ReasoningRun::reasoningTokens).sum();
        long latency = runs.stream().mapToLong(ReasoningRun::latencyMs).sum();
        int requests = runs.stream().mapToInt(ReasoningRun::apiRequests).sum();
        int totalTokens = promptTokens + completionTokens;
        String summary = "%d/%d · %d токенов · %.1f с".formatted(
                correct,
                runs.size(),
                totalTokens,
                latency / 1000.0
        );
        return new ReasoningCell(
                method,
                mode,
                runs.size(),
                correct,
                promptTokens,
                completionTokens,
                reasoningTokens,
                totalTokens,
                latency,
                requests,
                runs,
                summary
        );
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private record TokenUsage(int prompt, int completion, int reasoning) {
    }
}
