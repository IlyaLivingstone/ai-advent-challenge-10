package com.ilyalivingstone.aiadvent.day08;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AgentSession {

    private final String id;
    private final List<Map<String, Object>> messages = new ArrayList<>();
    private TokenStats tokenStats;

    public AgentSession(String id) {
        this.id = id;
        this.tokenStats = TokenStats.empty(0);
    }

    public AgentSession(String id, List<Map<String, Object>> history, int contextLimit) {
        this.id = id;
        this.messages.addAll(history);
        this.tokenStats = TokenStats.empty(contextLimit);
    }

    public AgentSession(String id, List<Map<String, Object>> history) {
        this.id = id;
        this.messages.addAll(history);
    }

    public String id() {
        return id;
    }

    public synchronized List<Map<String, Object>> messages() {
        return messages;
    }

    public synchronized void reset() {
        messages.clear();
        tokenStats = TokenStats.empty(tokenStats.contextLimit());
    }

    public synchronized TokenStats tokenStats() {
        return tokenStats;
    }

    public synchronized void tokenStats(TokenStats tokenStats) {
        this.tokenStats = tokenStats;
    }
}
