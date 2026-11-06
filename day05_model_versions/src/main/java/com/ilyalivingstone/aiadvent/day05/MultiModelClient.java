package com.ilyalivingstone.aiadvent.day05;

import com.ilyalivingstone.aiadvent.day05.llm.ChatCompletionResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MultiModelClient {

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public MultiModelClient(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public ChatCompletionResponse complete(ModelProfile profile, String prompt, int maxTokens) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", profile.model());
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("max_tokens", maxTokens);
            body.put("stream", false);
            if (profile.temperature() != null) {
                body.put("temperature", profile.temperature());
            }

            String apiKey = profile.apiKeyEnv() == null
                    ? "ollama"
                    : System.getenv().getOrDefault(profile.apiKeyEnv(), "");
            if (apiKey.isBlank()) {
                throw new IllegalStateException("Не задан " + profile.apiKeyEnv());
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(profile.endpoint()))
                    .timeout(Duration.ofSeconds(profile.timeoutSeconds()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "OpenAI/Python 1.0.0")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
            }
            ChatCompletionResponse completion = jsonMapper.readValue(response.body(), ChatCompletionResponse.class);
            if (completion.firstChoice() == null) {
                throw new IllegalStateException("Модель вернула пустой ответ");
            }
            return completion;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запрос прерван", error);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(error.getMessage(), error);
        }
    }
}
