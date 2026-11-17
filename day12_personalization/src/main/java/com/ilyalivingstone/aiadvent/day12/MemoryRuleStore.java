package com.ilyalivingstone.aiadvent.day12;

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
public class MemoryRuleStore {
    private final JsonMapper jsonMapper;
    private final Path file;
    private final Map<String, String> defaults = Map.of(
            "WORKING", "Текущая задача: цель, релиз/этап, следующий шаг, артефакт, дедлайн и ограничения именно этой задачи. Если в сообщении есть текущая задача — сохрани хотя бы одну запись.",
            "LONG_TERM", "Устойчивые факты: имя, роль, проект, платформа, команда, принятые решения и долговременные ограничения. Не сохраняй текущий этап, следующий шаг и дедлайн конкретной задачи."
    );

    public MemoryRuleStore(JsonMapper jsonMapper, @Value("${app.memory.dir:data/memory}") String directory) {
        this.jsonMapper = jsonMapper;
        this.file = Path.of(directory).resolve("rules.json");
    }

    public synchronized Map<String, String> rules() {
        Map<String, String> result = new LinkedHashMap<>(defaults);
        if (!Files.exists(file)) return result;
        try {
            var type = jsonMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class);
            Map<String, String> loaded = jsonMapper.readValue(Files.readString(file), type);
            result.putAll(loaded);
        } catch (Exception ignored) {}
        return result;
    }

    public synchronized void save(Map<String, String> rules) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("rules.json.tmp");
            Files.writeString(temp, jsonMapper.writeValueAsString(rules));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            throw new IllegalStateException("Не удалось сохранить правила памяти", error);
        }
    }
}
