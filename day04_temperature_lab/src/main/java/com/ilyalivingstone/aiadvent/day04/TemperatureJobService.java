package com.ilyalivingstone.aiadvent.day04;

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
public class TemperatureJobService {

    private final TemperatureExperimentService experimentService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, TemperatureJob> jobs = new ConcurrentHashMap<>();

    public TemperatureJobService(TemperatureExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    public TemperatureJob start() {
        String id = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        jobs.put(id, TemperatureJob.running(id, startedAt));
        executor.submit(() -> {
            try {
                TemperatureReport report = experimentService.run();
                jobs.put(id, TemperatureJob.completed(id, startedAt, Instant.now(), report));
            } catch (RuntimeException error) {
                jobs.put(id, TemperatureJob.failed(id, startedAt, Instant.now(), error.getMessage()));
            }
        });
        return jobs.get(id);
    }

    public Optional<TemperatureJob> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
