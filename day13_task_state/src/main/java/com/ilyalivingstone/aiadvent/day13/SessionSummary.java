package com.ilyalivingstone.aiadvent.day13;

import java.time.Instant;

public record SessionSummary(
        String id,
        Instant updatedAt,
        int messageCount,
        String summaryPreview,
        boolean compressionEnabled
) {
}
