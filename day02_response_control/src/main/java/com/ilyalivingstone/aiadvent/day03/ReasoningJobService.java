package com.ilyalivingstone.aiadvent.day03;

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
public class ReasoningJobService {

    private final ReasoningExperimentService experimentService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, ReasoningJob> jobs = new ConcurrentHashMap<>();

    public ReasoningJobService(ReasoningExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    public ReasoningJob start() {
        String id = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        jobs.put(id, ReasoningJob.running(id, startedAt));
        executor.submit(() -> {
            try {
                ReasoningReport report = experimentService.run();
                jobs.put(id, ReasoningJob.completed(id, startedAt, Instant.now(), report));
            } catch (RuntimeException error) {
                jobs.put(id, ReasoningJob.failed(id, startedAt, Instant.now(), error.getMessage()));
            }
        });
        return jobs.get(id);
    }

    public Optional<ReasoningJob> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
