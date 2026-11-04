package com.ilyalivingstone.aiadvent.day02.llm;

import java.net.http.HttpClient;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class DeepSeekClient {

    private final RestClient restClient;
    private final DeepSeekProperties properties;

    public DeepSeekClient(DeepSeekProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
    }

    public ChatCompletionResponse complete(
            String prompt,
            Integer maxTokens,
            List<String> stop,
            boolean jsonMode
    ) {
        return complete(prompt, maxTokens, stop, jsonMode, null, null);
    }

    public ChatCompletionResponse complete(
            String prompt,
            Integer maxTokens,
            List<String> stop,
            boolean jsonMode,
            String reasoningEffort,
            ChatCompletionRequest.Thinking thinking
    ) {
        if (!properties.hasApiKey()) {
            throw new DeepSeekException("DEEPSEEK_API_KEY не задан");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new DeepSeekException("Запрос не должен быть пустым");
        }

        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.model(),
                List.of(ChatMessage.user(prompt)),
                maxTokens == null ? properties.defaultMaxTokens() : maxTokens,
                stop == null || stop.isEmpty() ? null : List.copyOf(stop),
                jsonMode ? ChatCompletionRequest.ResponseFormat.jsonObject() : null,
                false,
                reasoningEffort,
                thinking
        );

        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            if (response == null || response.firstChoice() == null) {
                throw new DeepSeekException("DeepSeek вернул пустой ответ");
            }
            return response;
        } catch (RestClientResponseException error) {
            String detail = error.getResponseBodyAsString();
            if (detail.length() > 500) {
                detail = detail.substring(0, 500);
            }
            throw new DeepSeekException(
                    error.getStatusCode().value(),
                    "DeepSeek вернул HTTP " + error.getStatusCode().value() + ": " + detail,
                    error
            );
        }
    }
}
