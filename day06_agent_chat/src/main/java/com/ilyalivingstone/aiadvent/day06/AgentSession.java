package com.ilyalivingstone.aiadvent.day06;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AgentSession {

    private final String id;
    private final List<Map<String, Object>> messages = new ArrayList<>();

    public AgentSession(String id) {
        this.id = id;
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
