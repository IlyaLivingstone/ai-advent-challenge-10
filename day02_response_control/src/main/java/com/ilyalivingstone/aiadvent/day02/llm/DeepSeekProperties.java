package com.ilyalivingstone.aiadvent.day02.llm;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.deepseek")
public record DeepSeekProperties(
        URI baseUrl,
        String model,
        String apiKey,
        Duration timeout,
        int defaultMaxTokens
) {

    public DeepSeekProperties {
        baseUrl = baseUrl == null ? URI.create("https://api.deepseek.com") : baseUrl;
        model = model == null || model.isBlank() ? "deepseek-flash" : model;
        apiKey = apiKey == null ? "" : apiKey;
        timeout = timeout == null ? Duration.ofSeconds(90) : timeout;
        defaultMaxTokens = defaultMaxTokens <= 0 ? 1500 : defaultMaxTokens;
    }

    public boolean hasApiKey() {
        return !apiKey.isBlank();
    }
}
