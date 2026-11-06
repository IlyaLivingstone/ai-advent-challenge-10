package com.ilyalivingstone.aiadvent.day05;

import com.ilyalivingstone.aiadvent.day05.llm.ChatCompletionResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ModelExperimentService {

    private static final int MAX_TOKENS = 12000;

    private final ModelCatalog catalog;
    private final MultiModelClient client;
    private final AnswerEvaluator evaluator;
    private final ReasoningTaskCatalog taskCatalog;

    public ModelExperimentService(
            ModelCatalog catalog,
            MultiModelClient client,
            AnswerEvaluator evaluator,
            ReasoningTaskCatalog taskCatalog
    ) {
        this.catalog = catalog;
        this.client = client;
        this.evaluator = evaluator;
        this.taskCatalog = taskCatalog;
    }

    public ModelReport run() {
        Instant startedAt = Instant.now();
        List<ModelCell> cells = new ArrayList<>();
        for (ModelProfile profile : catalog.models()) {
            warmUp(profile);
            List<ModelRun> runs = new ArrayList<>();
            for (ReasoningTask task : taskCatalog.tasks()) {
                runs.add(runTask(profile, task));
            }
            cells.add(toCell(profile, runs));
        }
        Instant finishedAt = Instant.now();
        return new ModelReport(
                UUID.randomUUID().toString(), startedAt, finishedAt,
                finishedAt.toEpochMilli() - startedAt.toEpochMilli(),
                taskCatalog.tasks().size(),
                cells
        );
    }

    private void warmUp(ModelProfile profile) {
        try {
            client.complete(profile, "Ответь одним словом: ok", 32);
        } catch (RuntimeException ignored) {
            // Warm-up is best effort; measured runs will report the actual failure.
        }
    }

    private ModelRun runTask(ModelProfile profile, ReasoningTask task) {
        long started = System.nanoTime();
        try {
            ChatCompletionResponse response = client.complete(profile, promptFor(task), MAX_TOKENS);
            String content = response.firstChoice().message() == null
                    ? ""
                    : response.firstChoice().message().content();
            Evaluation evaluation = evaluator.evaluate(content, task);
            int promptTokens = response.usage() == null ? 0 : response.usage().promptTokens();
            int completionTokens = response.usage() == null ? 0 : response.usage().completionTokens();
            int totalTokens = promptTokens + completionTokens;
            double cost = (promptTokens * profile.inputPricePerMillion()
                    + completionTokens * profile.outputPricePerMillion()) / 1_000_000.0;
            return new ModelRun(
                    profile.id(), task.id(), task.title(), task.category(), task.expectedAnswer(),
                    evaluation.finalAnswer(), !evaluation.finalAnswer().isBlank(),
                    evaluation.correct(), promptTokens, completionTokens, totalTokens,
                    elapsedMs(started), cost, content, null
            );
        } catch (RuntimeException error) {
            return new ModelRun(
                    profile.id(), task.id(), task.title(), task.category(), task.expectedAnswer(),
                    "", false, false, 0, 0, 0, elapsedMs(started), 0.0, "", error.getMessage()
            );
        }
    }

    private String promptFor(ReasoningTask task) {
        return task.question() + "\n\nВ конце ответа добавь ровно одну строку:\nFINAL: <краткий ответ>";
    }

    private ModelCell toCell(ModelProfile profile, List<ModelRun> runs) {
        int correct = (int) runs.stream().filter(ModelRun::correct).count();
        int format = (int) runs.stream().filter(ModelRun::formatValid).count();
        int tokens = runs.stream().mapToInt(ModelRun::totalTokens).sum();
        long latency = runs.stream().mapToLong(ModelRun::latencyMs).sum();
        double cost = runs.stream().mapToDouble(ModelRun::costUsd).sum();
        String summary = "%d/%d верно · %d tok · %.1f с".formatted(
                correct, runs.size(), tokens, latency / 1000.0
        );
        return new ModelCell(
                profile.id(), profile.title(), profile.role(), runs.size(), correct, format,
                tokens, latency, cost, runs.isEmpty() ? 0 : latency / (double) runs.size(),
                runs, summary
        );
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
