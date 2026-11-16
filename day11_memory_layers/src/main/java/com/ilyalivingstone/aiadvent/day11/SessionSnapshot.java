package com.ilyalivingstone.aiadvent.day11;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SessionSnapshot(
        String id,
        Instant updatedAt,
        List<Map<String, Object>> messages,
        String summary,
        int summarizedCount,
        boolean compressionEnabled,
        TokenStats tokenStats,
        ContextStrategy strategy,
        java.util.Map<String, String> facts,
        java.util.Map<String, String> working,
        Boolean shortTermEnabled,
        Boolean workingEnabled,
        Boolean longTermEnabled
) {
}
