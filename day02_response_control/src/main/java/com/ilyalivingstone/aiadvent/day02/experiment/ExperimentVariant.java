package com.ilyalivingstone.aiadvent.day02.experiment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ExperimentVariant {

    BASELINE("baseline", "Без ограничений"),
    EXPLICIT_FORMAT("explicit_format", "Явный формат в промпте"),
    MAX_TOKENS("max_tokens", "JSON + ограничение max_tokens"),
    STOP_SEQUENCE("stop_sequence", "JSON + stop sequence"),
    STRICT_SCHEMA("strict_schema", "Строгая JSON-схема");

    private final String id;
    private final String title;

    ExperimentVariant(String id, String title) {
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
    public static ExperimentVariant fromId(String id) {
        for (ExperimentVariant variant : values()) {
            if (variant.id.equals(id)) {
                return variant;
            }
        }
        throw new IllegalArgumentException("Неизвестный вариант эксперимента: " + id);
    }
}
