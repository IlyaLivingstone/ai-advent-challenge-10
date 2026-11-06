package com.ilyalivingstone.aiadvent.day05;

public record ModelProfile(
        String id,
        String title,
        String role,
        String endpoint,
        String model,
        String apiKeyEnv,
        int timeoutSeconds,
        double inputPricePerMillion,
        double outputPricePerMillion,
        Double temperature
) {
}
