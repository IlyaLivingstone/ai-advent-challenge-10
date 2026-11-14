package com.ilyalivingstone.aiadvent.day10;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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

    public interface StreamListener {
        default void onRequest(String requestJson) {}

        default void onReasoningDelta(String delta) {}

        default void onContentDelta(String delta) {}
    }

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
            body.put("max_tokens", 8000);
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
            String reasoning = message.hasNonNull("reasoning_content") ? message.get("reasoning_content").asString() : "";
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
            return new LlmTurn(new LlmReply(content, reasoning, calls), requestJson, responseJson, usage);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запрос к модели прерван", error);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(error.getMessage(), error);
        }
    }
    public LlmTurn chatStream(List<Map<String, Object>> messages, List<ToolDefinition> tools, StreamListener listener) {
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
            body.put("max_tokens", 8000);
            body.put("stream", true);
            body.put("stream_options", Map.of("include_usage", true));

            String requestJson = jsonMapper.writeValueAsString(body);
            listener.onRequest(requestJson);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(180))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody;
                try (var stream = response.body()) {
                    errorBody = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                throw new AgentLlmException(
                        "DeepSeek HTTP " + response.statusCode() + ": " + errorBody,
                        requestJson,
                        errorBody
                );
            }

            StringBuilder content = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            Map<Integer, ToolCallBuilder> callBuilders = new java.util.TreeMap<>();
            TokenUsage usage = new TokenUsage(0, 0, 0);
            String finishReason = "";
            try (var reader = new BufferedReader(new InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        continue;
                    }
                    JsonNode chunk = jsonMapper.readTree(data);
                    JsonNode usageNode = chunk.path("usage");
                    if (usageNode.isObject()) {
                        usage = new TokenUsage(
                                usageNode.path("prompt_tokens").asInt(0),
                                usageNode.path("completion_tokens").asInt(0),
                                usageNode.path("total_tokens").asInt(0)
                        );
                    }
                    JsonNode choice = chunk.path("choices").path(0);
                    if (choice.hasNonNull("finish_reason")) {
                        finishReason = choice.get("finish_reason").asString();
                    }
                    JsonNode delta = choice.path("delta");
                    String reasoningDelta = delta.hasNonNull("reasoning_content") ? delta.get("reasoning_content").asString() : "";
                    if (!reasoningDelta.isEmpty()) {
                        reasoning.append(reasoningDelta);
                        listener.onReasoningDelta(reasoningDelta);
                    }
                    String contentDelta = delta.hasNonNull("content") ? delta.get("content").asString() : "";
                    if (!contentDelta.isEmpty()) {
                        content.append(contentDelta);
                        listener.onContentDelta(contentDelta);
                    }
                    JsonNode toolCalls = delta.path("tool_calls");
                    if (toolCalls.isArray()) {
                        for (JsonNode part : toolCalls) {
                            int index = part.path("index").asInt(0);
                            ToolCallBuilder builder = callBuilders.computeIfAbsent(index, ignored -> new ToolCallBuilder());
                            if (part.hasNonNull("id")) builder.id = part.get("id").asString();
                            JsonNode function = part.path("function");
                            if (function.hasNonNull("name")) builder.name += function.get("name").asString();
                            if (function.hasNonNull("arguments")) builder.arguments += function.get("arguments").asString();
                        }
                    }
                }
            }

            List<ToolCall> calls = callBuilders.values().stream()
                    .map(builder -> new ToolCall(builder.id, builder.name, builder.arguments))
                    .toList();
            LlmReply reply = new LlmReply(content.toString(), reasoning.toString(), calls);
            Map<String, Object> responseView = new LinkedHashMap<>();
            responseView.put("streamed", true);
            responseView.put("finish_reason", finishReason);
            responseView.put("message", Map.of(
                    "role", "assistant",
                    "content", content.toString(),
                    "reasoning_content", reasoning.toString(),
                    "tool_calls", calls.stream().map(call -> Map.of(
                            "id", call.id(),
                            "type", "function",
                            "function", Map.of("name", call.name(), "arguments", call.arguments())
                    )).toList()
            ));
            responseView.put("usage", Map.of(
                    "prompt_tokens", usage.promptTokens(),
                    "completion_tokens", usage.completionTokens(),
                    "total_tokens", usage.totalTokens()
            ));
            String responseJson = jsonMapper.writeValueAsString(responseView);
            return new LlmTurn(reply, requestJson, responseJson, usage);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запрос к модели прерван", error);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(error.getMessage(), error);
        }
    }

    private static final class ToolCallBuilder {
        private String id = "";
        private String name = "";
        private String arguments = "";
    }

}
