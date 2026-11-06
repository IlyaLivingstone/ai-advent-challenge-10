package com.ilyalivingstone.aiadvent.day04;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PromptKind {

    EXACT("exact", "Один правильный ответ"),
    CREATIVE("creative", "Генерация");

    private final String id;
    private final String title;

    PromptKind(String id, String title) {
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
    public static PromptKind fromId(String id) {
        for (PromptKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Неизвестный тип задачи: " + id);
    }
}
