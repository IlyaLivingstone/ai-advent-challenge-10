package com.ilyalivingstone.aiadvent.day02.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(
        String model,
        List<Choice> choices,
        Usage usage
) {

    public Choice firstChoice() {
        return choices == null || choices.isEmpty() ? null : choices.getFirst();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            ChatMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens,
            @JsonProperty("completion_tokens_details") CompletionTokensDetails completionTokensDetails
    ) {

        public int reasoningTokens() {
            return completionTokensDetails == null ? 0 : completionTokensDetails.reasoningTokens();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompletionTokensDetails(
            @JsonProperty("reasoning_tokens") int reasoningTokens
    ) {
    }
}
