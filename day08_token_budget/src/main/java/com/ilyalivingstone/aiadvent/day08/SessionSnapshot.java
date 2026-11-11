package com.ilyalivingstone.aiadvent.day08;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SessionSnapshot(
        String id,
        Instant updatedAt,
        List<Map<String, Object>> messages
) {
}
