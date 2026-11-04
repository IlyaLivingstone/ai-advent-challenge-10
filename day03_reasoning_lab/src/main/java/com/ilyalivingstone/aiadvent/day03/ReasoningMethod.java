package com.ilyalivingstone.aiadvent.day03;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReasoningMethod {

    DIRECT("direct", "Прямой ответ"),
    STEP_BY_STEP("step_by_step", "Решай пошагово"),
    META_PROMPT("meta_prompt", "Сначала промпт, потом решение"),
    EXPERT_PANEL("expert_panel", "Группа экспертов");

    private final String id;
    private final String title;

    ReasoningMethod(String id, String title) {
        this.id = id;
        this.title = title;
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    @JsonCreator
    public static ReasoningMethod fromId(String id) {
        for (ReasoningMethod method : values()) {
            if (method.id.equals(id)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Неизвестный способ решения: " + id);
    }
}
