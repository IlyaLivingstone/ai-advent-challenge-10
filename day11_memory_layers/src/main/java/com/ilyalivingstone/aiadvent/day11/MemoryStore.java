package com.ilyalivingstone.aiadvent.day11;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MemoryStore {
    private final JsonMapper jsonMapper;
    private final Path file;

    public MemoryStore(JsonMapper jsonMapper, @Value("${app.memory.dir:data/memory}") String directory) {
        this.jsonMapper = jsonMapper;
        this.file = Path.of(directory).resolve("long-term.json");
    }

    public synchronized Map<String, String> longTerm() {
        if (!Files.exists(file)) {
            Map<String, String> seed = new LinkedHashMap<>();
            seed.put("project.name", "Колобок");
            seed.put("project.deadline", "15 декабря");
            seed.put("project.budget", "300 000 ₽");
            seed.put("project.restriction", "нельзя использовать внешние API");
            seed.put("project.stack", "Java");
            save(seed);
            return seed;
        }
        try {
            var type = jsonMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class);
            return jsonMapper.readValue(Files.readString(file), type);
        } catch (Exception error) {
            return new LinkedHashMap<>();
        }
    }

    public synchronized void put(String key, String value) {
        Map<String, String> data = new LinkedHashMap<>(longTerm());
        data.put(key, value);
        save(data);
    }

    public synchronized void remove(String key) {
        Map<String, String> data = new LinkedHashMap<>(longTerm());
        data.remove(key);
        save(data);
    }

    public synchronized void merge(Map<String, String> values) {
        Map<String, String> data = new LinkedHashMap<>(longTerm());
        data.putAll(values);
        save(data);
    }

    public synchronized void clear() {
        save(new LinkedHashMap<>());
    }

    private void save(Map<String, String> data) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("long-term.json.tmp");
            Files.writeString(temp, jsonMapper.writeValueAsString(data));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            throw new IllegalStateException("Не удалось сохранить долговременную память", error);
        }
    }
}
