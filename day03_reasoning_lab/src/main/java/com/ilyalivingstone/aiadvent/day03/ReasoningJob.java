package com.ilyalivingstone.aiadvent.day03;

import java.time.Instant;

public record ReasoningJob(
        String id,
        ReasoningJobStatus status,
        Instant startedAt,
        Instant finishedAt,
        ReasoningReport report,
        String error
) {

    public static ReasoningJob running(String id, Instant startedAt) {
        return new ReasoningJob(id, ReasoningJobStatus.RUNNING, startedAt, null, null, null);
    }

    public static ReasoningJob completed(String id, Instant startedAt, Instant finishedAt, ReasoningReport report) {
        return new ReasoningJob(id, ReasoningJobStatus.COMPLETED, startedAt, finishedAt, report, null);
    }

    public static ReasoningJob failed(String id, Instant startedAt, Instant finishedAt, String error) {
        return new ReasoningJob(id, ReasoningJobStatus.FAILED, startedAt, finishedAt, null, error);
    }
}
