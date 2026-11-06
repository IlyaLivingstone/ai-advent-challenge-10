package com.ilyalivingstone.aiadvent.day05;

import java.time.Instant;

public record ModelJob(
        String id,
        ModelJobStatus status,
        Instant startedAt,
        Instant finishedAt,
        ModelReport report,
        String error
) {
    public static ModelJob running(String id, Instant startedAt) {
        return new ModelJob(id, ModelJobStatus.RUNNING, startedAt, null, null, null);
    }

    public static ModelJob completed(String id, Instant startedAt, Instant finishedAt, ModelReport report) {
        return new ModelJob(id, ModelJobStatus.COMPLETED, startedAt, finishedAt, report, null);
    }

    public static ModelJob failed(String id, Instant startedAt, Instant finishedAt, String error) {
        return new ModelJob(id, ModelJobStatus.FAILED, startedAt, finishedAt, null, error);
    }
}
