package com.ilyalivingstone.aiadvent.day08;

public class AgentLlmException extends RuntimeException {
    private final String requestJson;
    private final String responseJson;

    public AgentLlmException(String message, String requestJson, String responseJson) {
        super(message);
        this.requestJson = requestJson;
        this.responseJson = responseJson;
    }

    public String requestJson() {
        return requestJson;
    }

    public String responseJson() {
        return responseJson;
    }
}
