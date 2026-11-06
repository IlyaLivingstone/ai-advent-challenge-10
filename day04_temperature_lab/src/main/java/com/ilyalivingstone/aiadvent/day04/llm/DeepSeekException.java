package com.ilyalivingstone.aiadvent.day04.llm;

public class DeepSeekException extends RuntimeException {

    private final Integer statusCode;

    public DeepSeekException(String message) {
        this(null, message, null);
    }

    public DeepSeekException(Integer statusCode, String message) {
        this(statusCode, message, null);
    }

    public DeepSeekException(Integer statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer statusCode() {
        return statusCode;
    }
}
