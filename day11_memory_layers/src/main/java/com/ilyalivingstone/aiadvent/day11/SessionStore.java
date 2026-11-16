package com.ilyalivingstone.aiadvent.day11;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SessionStore {

    private final JsonMapper jsonMapper;
    private final Path directory;

    public SessionStore(
            JsonMapper jsonMapper,
            @Value("${app.agent.storage-dir:data/sessions}") String directory
    ) {
        this.jsonMapper = jsonMapper;
        this.directory = Path.of(directory);
    }

    public SessionSnapshot load(String id) {
        validateId(id);
        Path file = fileFor(id);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return jsonMapper.readValue(Files.readString(file), SessionSnapshot.class);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Не удалось прочитать сессию " + id, error);
        }
    }

    public void save(String id, List<Map<String, Object>> messages, String summary, int summarizedCount,
                     boolean compressionEnabled, TokenStats tokenStats,
                     ContextStrategy strategy, java.util.Map<String, String> facts,
                     java.util.Map<String, String> working,
                     boolean shortTermEnabled, boolean workingEnabled, boolean longTermEnabled) {
        validateId(id);
        try {
            Files.createDirectories(directory);
            Path file = fileFor(id);
            Path temp = directory.resolve(id + ".json.tmp");
            SessionSnapshot snapshot = new SessionSnapshot(
                    id, Instant.now(), new ArrayList<>(messages), summary, summarizedCount, compressionEnabled,
                    tokenStats, strategy, facts == null ? java.util.Map.of() : new java.util.LinkedHashMap<>(facts),
                    working == null ? java.util.Map.of() : new java.util.LinkedHashMap<>(working),
                    shortTermEnabled, workingEnabled, longTermEnabled
            );
            Files.writeString(temp, jsonMapper.writeValueAsString(snapshot));
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Не удалось сохранить сессию " + id, error);
        }
    }

    public List<SessionSnapshot> list() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> {
                        try {
                            return jsonMapper.readValue(Files.readString(path), SessionSnapshot.class);
                        } catch (Exception error) {
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .sorted(java.util.Comparator.comparing(SessionSnapshot::updatedAt,
                            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                    .toList();
        } catch (IOException error) {
            throw new IllegalStateException("Не удалось получить список сессий", error);
        }
    }

    public void delete(String id) {
        validateId(id);
        try {
            Files.deleteIfExists(fileFor(id));
        } catch (IOException error) {
            throw new IllegalStateException("Не удалось удалить сессию " + id, error);
        }
    }

    private Path fileFor(String id) {
        return directory.resolve(id + ".json");
    }

    private void validateId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9-]{1,80}")) {
            throw new IllegalArgumentException("Некорректный id сессии");
        }
    }
}
