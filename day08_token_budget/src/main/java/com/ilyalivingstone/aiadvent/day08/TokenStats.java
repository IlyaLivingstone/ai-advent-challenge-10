package com.ilyalivingstone.aiadvent.day08;

public record TokenStats(
        int currentRequest,
        int history,
        int response,
        int total,
        int calls,
        int contextLimit,
        double callCost,
        double totalCost,
        int failedRequests
) {
    public static TokenStats empty(int contextLimit) {
        return new TokenStats(0, 0, 0, 0, 0, contextLimit, 0.0, 0.0, 0);
    }

    public TokenStats add(int currentRequest, int history, int response, double callCost) {
        return new TokenStats(
                currentRequest, history, response,
                total + history + response, calls + 1,
                contextLimit, callCost, totalCost + callCost, failedRequests
        );
    }

    public TokenStats failed() {
        return new TokenStats(
                currentRequest, history, response, total, calls,
                contextLimit, callCost, totalCost, failedRequests + 1
        );
    }
}
