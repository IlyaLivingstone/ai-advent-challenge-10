package com.ilyalivingstone.aiadvent.day04;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TemperatureLevel {

    ZERO("0", "0", 0.0),
    BALANCED("0.7", "0.7", 0.7),
    CREATIVE("1.2", "1.2", 1.2);

    private final String id;
    private final String label;
    private final double value;

    TemperatureLevel(String id, String label, double value) {
        this.id = id;
        this.label = label;
        this.value = value;
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public double value() {
        return value;
    }

    @JsonCreator
    public static TemperatureLevel fromId(String id) {
        for (TemperatureLevel level : values()) {
            if (level.id.equals(id)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Неизвестная температура: " + id);
    }
}
