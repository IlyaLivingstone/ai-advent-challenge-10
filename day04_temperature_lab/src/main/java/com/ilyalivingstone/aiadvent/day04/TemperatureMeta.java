package com.ilyalivingstone.aiadvent.day04;

import java.util.List;

public record TemperatureMeta(
        String model,
        int runsPerCombination,
        int totalRequests,
        List<TaskMeta> tasks,
        List<TemperatureMetaItem> temperatures
) {

    public record TaskMeta(String id, String kind, String kindTitle, String title, String prompt) {
    }

    public record TemperatureMetaItem(String id, String label, double value) {
    }
}
