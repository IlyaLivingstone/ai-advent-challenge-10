package com.ilyalivingstone.aiadvent.day08;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AgentLlmClient {

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public AgentLlmClient(
            JsonMapper jsonMapper,
            @Value("${app.deepseek.base-url}") String baseUrl,
            @Value("${app.deepseek.api-key:}") String apiKey,
            @Value("${app.deepseek.model}") String model
    ) {
        this.jsonMapper = jsonMapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    public LlmTurn chat(List<Map<String, Object>> messages, List<ToolDefinition> tools) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DEEPSEEK_API_KEY не задан");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("tools", tools.stream().map(ToolDefinition::toOpenAi).toList());
            body.put("tool_choice", "auto");
            body.put("temperature", 0);
            body.put("max_tokens", 2000);
            body.put("stream", false);

            String requestJson = jsonMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AgentLlmException(
                        "DeepSeek HTTP " + response.statusCode() + ": " + response.body(),
                        requestJson,
                        response.body()
                );
            }
            String responseJson = response.body();
            JsonNode root = jsonMapper.readTree(responseJson);
            JsonNode message = root.path("choices").path(0).path("message");
            JsonNode usageNode = root.path("usage");
            TokenUsage usage = new TokenUsage(
                    usageNode.path("prompt_tokens").asInt(0),
                    usageNode.path("completion_tokens").asInt(0),
                    usageNode.path("total_tokens").asInt(0)
            );
            String content = message.hasNonNull("content") ? message.get("content").asString() : "";
            List<ToolCall> calls = new ArrayList<>();
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode call : toolCalls) {
                    JsonNode function = call.path("function");
                    calls.add(new ToolCall(
                            call.path("id").asString(),
                            function.path("name").asString(),
                            function.path("arguments").asString()
                    ));
                }
            }
            return new LlmTurn(new LlmReply(content, calls), requestJson, responseJson, usage);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запрос к модели прерван", error);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(error.getMessage(), error);
        }
    }
}
