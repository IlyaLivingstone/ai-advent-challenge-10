package com.ilyalivingstone.aiadvent.day03;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReasoningMode {

    MINIMAL("low", "Минимальное (reasoning off)", false, null),
    MAXIMUM("high", "Максимальное", true, "max");

    private final String id;
    private final String title;
    private final boolean thinkingEnabled;
    private final String reasoningEffort;

    ReasoningMode(String id, String title, boolean thinkingEnabled, String reasoningEffort) {
        this.id = id;
        this.title = title;
        this.thinkingEnabled = thinkingEnabled;
        this.reasoningEffort = reasoningEffort;
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public boolean thinkingEnabled() {
        return thinkingEnabled;
    }

    public String reasoningEffort() {
        return reasoningEffort;
    }

    @JsonCreator
    public static ReasoningMode fromId(String id) {
        for (ReasoningMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Неизвестный режим рассуждения: " + id);
    }
}
