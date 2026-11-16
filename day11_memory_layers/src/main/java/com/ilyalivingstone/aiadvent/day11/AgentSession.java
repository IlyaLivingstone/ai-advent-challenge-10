package com.ilyalivingstone.aiadvent.day11;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AgentSession {

    private final String id;
    private final List<Map<String, Object>> messages = new ArrayList<>();
    private TokenStats tokenStats;
    private String summary = "";
    private int summarizedCount;
    private boolean compressionEnabled = true;
    private ContextStrategy strategy = ContextStrategy.SUMMARY;
    private final java.util.Map<String, String> facts = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> working = new java.util.LinkedHashMap<>();
    private boolean shortTermEnabled = true;
    private boolean workingEnabled = true;
    private boolean longTermEnabled = true;

    public AgentSession(String id) {
        this.id = id;
        this.tokenStats = TokenStats.empty(0);
    }

    public AgentSession(String id, List<Map<String, Object>> history, int contextLimit) {
        this.id = id;
        this.messages.addAll(history);
        this.tokenStats = TokenStats.empty(contextLimit);
    }

    public AgentSession(String id, SessionSnapshot snapshot, int contextLimit) {
        this.id = id;
        if (snapshot != null) {
            this.messages.addAll(snapshot.messages() == null ? List.of() : snapshot.messages());
            this.summary = snapshot.summary() == null ? "" : snapshot.summary();
            this.summarizedCount = snapshot.summarizedCount();
            this.compressionEnabled = snapshot.compressionEnabled();
            this.strategy = snapshot.strategy() == null ? ContextStrategy.SUMMARY : snapshot.strategy();
            if (snapshot.facts() != null) this.facts.putAll(snapshot.facts());
            if (snapshot.working() != null) this.working.putAll(snapshot.working());
            this.shortTermEnabled = snapshot.shortTermEnabled() == null || snapshot.shortTermEnabled();
            this.workingEnabled = snapshot.workingEnabled() == null || snapshot.workingEnabled();
            this.longTermEnabled = snapshot.longTermEnabled() == null || snapshot.longTermEnabled();
            this.tokenStats = snapshot.tokenStats() == null ? TokenStats.empty(contextLimit) : snapshot.tokenStats();
        } else {
            this.tokenStats = TokenStats.empty(contextLimit);
        }
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
        summary = "";
        summarizedCount = 0;
        compressionEnabled = true;
        strategy = ContextStrategy.SUMMARY;
        facts.clear();
        working.clear();
        shortTermEnabled = true;
        workingEnabled = true;
        longTermEnabled = true;
        tokenStats = TokenStats.empty(tokenStats.contextLimit());
    }

    public synchronized String summary() { return summary; }
    public synchronized int summarizedCount() { return summarizedCount; }
    public synchronized boolean compressionEnabled() { return compressionEnabled; }
    public synchronized ContextStrategy strategy() { return strategy; }
    public synchronized void strategy(ContextStrategy strategy) { this.strategy = strategy; }
    public synchronized java.util.Map<String, String> facts() { return facts; }
    public synchronized java.util.Map<String, String> working() { return working; }
    public synchronized boolean shortTermEnabled() { return shortTermEnabled; }
    public synchronized boolean workingEnabled() { return workingEnabled; }
    public synchronized boolean longTermEnabled() { return longTermEnabled; }
    public synchronized void enabled(MemoryLayer layer, boolean enabled) {
        switch (layer) {
            case SHORT_TERM -> shortTermEnabled = enabled;
            case WORKING -> workingEnabled = enabled;
            case LONG_TERM -> longTermEnabled = enabled;
        }
    }
    public synchronized void compressionEnabled(boolean enabled) { this.compressionEnabled = enabled; }
    public synchronized void summary(String summary, int summarizedCount) {
        this.summary = summary == null ? "" : summary;
        this.summarizedCount = summarizedCount;
    }

    public synchronized TokenStats tokenStats() {
        return tokenStats;
    }

    public synchronized void tokenStats(TokenStats tokenStats) {
        this.tokenStats = tokenStats;
    }
}
