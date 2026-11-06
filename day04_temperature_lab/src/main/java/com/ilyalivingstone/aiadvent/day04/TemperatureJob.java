package com.ilyalivingstone.aiadvent.day04;

import java.time.Instant;

public record TemperatureJob(
        String id,
        TemperatureJobStatus status,
        Instant startedAt,
        Instant finishedAt,
        TemperatureReport report,
        String error
) {

    public static TemperatureJob running(String id, Instant startedAt) {
        return new TemperatureJob(id, TemperatureJobStatus.RUNNING, startedAt, null, null, null);
    }

    public static TemperatureJob completed(String id, Instant startedAt, Instant finishedAt, TemperatureReport report) {
        return new TemperatureJob(id, TemperatureJobStatus.COMPLETED, startedAt, finishedAt, report, null);
    }

    public static TemperatureJob failed(String id, Instant startedAt, Instant finishedAt, String error) {
        return new TemperatureJob(id, TemperatureJobStatus.FAILED, startedAt, finishedAt, null, error);
    }
}
