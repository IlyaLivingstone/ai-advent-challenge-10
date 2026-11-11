package com.ilyalivingstone.aiadvent.day07;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AgentSession {

    private final String id;
    private final List<Map<String, Object>> messages = new ArrayList<>();

    public AgentSession(String id) {
        this.id = id;
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
    }
}
