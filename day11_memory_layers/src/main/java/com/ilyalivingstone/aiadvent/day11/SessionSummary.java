package com.ilyalivingstone.aiadvent.day11;

import java.time.Instant;

public record SessionSummary(
        String id,
        Instant updatedAt,
        int messageCount,
        String summaryPreview,
        boolean compressionEnabled
) {
}
