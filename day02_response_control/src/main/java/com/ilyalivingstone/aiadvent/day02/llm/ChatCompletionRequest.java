package com.ilyalivingstone.aiadvent.day02.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        List<String> stop,
        @JsonProperty("response_format") ResponseFormat responseFormat,
        boolean stream,
        @JsonProperty("reasoning_effort") String reasoningEffort,
        Thinking thinking
) {

    public ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            Integer maxTokens,
            List<String> stop,
            ResponseFormat responseFormat,
            boolean stream
    ) {
        this(model, messages, maxTokens, stop, responseFormat, stream, null, null);
    }

    public record ResponseFormat(String type) {

        public static ResponseFormat jsonObject() {
            return new ResponseFormat("json_object");
        }
    }

    public record Thinking(String type) {

        public static Thinking enabled() {
            return new Thinking("enabled");
        }

        public static Thinking disabled() {
            return new Thinking("disabled");
        }
    }
}
