package com.ilyalivingstone.aiadvent.day02.experiment;

import java.time.Instant;

public record ExperimentJob(
        String id,
        JobStatus status,
        Instant startedAt,
        Instant finishedAt,
        ExperimentReport report,
        String error
) {

    public static ExperimentJob running(String id, Instant startedAt) {
        return new ExperimentJob(id, JobStatus.RUNNING, startedAt, null, null, null);
    }

    public static ExperimentJob completed(
            String id,
            Instant startedAt,
            Instant finishedAt,
            ExperimentReport report
    ) {
        return new ExperimentJob(id, JobStatus.COMPLETED, startedAt, finishedAt, report, null);
    }

    public static ExperimentJob failed(
            String id,
            Instant startedAt,
            Instant finishedAt,
            String error
    ) {
        return new ExperimentJob(id, JobStatus.FAILED, startedAt, finishedAt, null, error);
    }
}
