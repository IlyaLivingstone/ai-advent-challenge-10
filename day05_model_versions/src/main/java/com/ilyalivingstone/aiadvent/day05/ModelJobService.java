package com.ilyalivingstone.aiadvent.day05;

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
public class ModelJobService {

    private final ModelExperimentService experimentService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, ModelJob> jobs = new ConcurrentHashMap<>();

    public ModelJobService(ModelExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    public ModelJob start() {
        String id = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        jobs.put(id, ModelJob.running(id, startedAt));
        executor.submit(() -> {
            try {
                jobs.put(id, ModelJob.completed(id, startedAt, Instant.now(), experimentService.run()));
            } catch (RuntimeException error) {
                jobs.put(id, ModelJob.failed(id, startedAt, Instant.now(), error.getMessage()));
            }
        });
        return jobs.get(id);
    }

    public Optional<ModelJob> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
