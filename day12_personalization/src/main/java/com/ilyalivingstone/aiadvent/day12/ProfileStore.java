package com.ilyalivingstone.aiadvent.day12;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ProfileStore {
    private final JsonMapper jsonMapper;
    private final Path file;

    public ProfileStore(JsonMapper jsonMapper, @Value("${app.profile.dir:data/profiles}") String directory) {
        this.jsonMapper = jsonMapper;
        this.file = Path.of(directory).resolve("profiles.json");
    }

    public synchronized List<UserProfile> profiles() {
        if (!Files.exists(file)) {
            List<UserProfile> seed = List.of(
                    new UserProfile("brief", "Кратко и формально", "кратко", "формальный", "без кода", "не более 3 предложений", "MANUAL"),
                    new UserProfile("detailed", "Подробно и с кодом", "подробно", "дружелюбный", "с примерами кода", "показывай шаги и примеры", "MANUAL"),
                    new UserProfile("informal", "Неформально", "средне", "неформальный", "по запросу", "можно шутить, но без воды", "MANUAL")
            );
            save(seed);
            return seed;
        }
        try {
            var type = jsonMapper.getTypeFactory().constructCollectionType(ArrayList.class, UserProfile.class);
            return jsonMapper.readValue(Files.readString(file), type);
        } catch (Exception error) {
            return List.of();
        }
    }

    public synchronized UserProfile byId(String id) {
        return profiles().stream().filter(profile -> profile.id().equals(id)).findFirst().orElse(null);
    }

    public synchronized void saveProfile(UserProfile profile) {
        List<UserProfile> all = new ArrayList<>(profiles());
        all.removeIf(existing -> existing.id().equals(profile.id()));
        all.add(profile);
        save(all);
    }

    public synchronized void delete(String id) {
        List<UserProfile> all = new ArrayList<>(profiles());
        all.removeIf(profile -> profile.id().equals(id));
        save(all);
    }

    private void save(List<UserProfile> profiles) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("profiles.json.tmp");
            Files.writeString(temp, jsonMapper.writeValueAsString(profiles));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            throw new IllegalStateException("Не удалось сохранить профили", error);
        }
    }
}
