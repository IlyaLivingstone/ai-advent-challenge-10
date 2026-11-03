package com.ilyalivingstone.aiadvent.day02.experiment;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class ExperimentJobService {

    private final ExperimentService experimentService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, ExperimentJob> jobs = new ConcurrentHashMap<>();

    public ExperimentJobService(ExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    public ExperimentJob start(int repeats, String basePrompt) {
        if (repeats < 1 || repeats > ExperimentService.MAX_REPEATS) {
            throw new IllegalArgumentException(
                    "Количество повторов должно быть от 1 до " + ExperimentService.MAX_REPEATS
            );
        }

        String id = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        jobs.put(id, ExperimentJob.running(id, startedAt));

        executor.submit(() -> {
            try {
                ExperimentReport report = experimentService.run(repeats, basePrompt);
                jobs.put(id, ExperimentJob.completed(id, startedAt, Instant.now(), report));
            } catch (RuntimeException error) {
                jobs.put(id, ExperimentJob.failed(id, startedAt, Instant.now(), error.getMessage()));
            }
        });
        return jobs.get(id);
    }

    public Optional<ExperimentJob> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
