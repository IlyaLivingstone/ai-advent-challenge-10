package com.ilyalivingstone.aiadvent.day12;

public record UserProfile(
        String id,
        String name,
        String length,
        String tone,
        String code,
        String restrictions,
        String source
) {
}
