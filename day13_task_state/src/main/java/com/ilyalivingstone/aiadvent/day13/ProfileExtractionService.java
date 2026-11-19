package com.ilyalivingstone.aiadvent.day13;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ProfileExtractionService {
    private final AgentLlmClient client;
    private final JsonMapper jsonMapper;

    public ProfileExtractionService(AgentLlmClient client, JsonMapper jsonMapper) {
        this.client = client;
        this.jsonMapper = jsonMapper;
    }

    public Map<String, String> changes(UserProfile current, String userMessage) {
        Map<String, String> result = new LinkedHashMap<>();
        if (current == null || userMessage == null || userMessage.isBlank()) return result;
        try {
            String prompt = """
                    Текущий профиль:
                    name: %s
                    length: %s
                    tone: %s
                    code: %s
                    restrictions: %s

                    Новое сообщение пользователя:
                    %s

                    Если пользователь явно попросил изменить длину, тон, показ кода или ограничения,
                    верни только изменённые поля JSON: {"length":"...","tone":"...","code":"...","restrictions":"..."}.
                    Не меняй name. Если предпочтений нет — верни {}.
                    """.formatted(current.name(), current.length(), current.tone(), current.code(),
                    current.restrictions(), userMessage);
            var turn = client.chat(List.of(
                    Map.of("role", "system", "content", "Ты извлекаешь только явные изменения предпочтений."),
                    Map.of("role", "user", "content", prompt)
            ), List.of());
            String content = turn.reply().content();
            if (content == null || content.isBlank()) return result;
            content = content.replace("```json", "").replace("```", "").strip();
            JsonNode root = jsonMapper.readTree(content);
            if (!root.isObject()) return result;
            for (String field : List.of("length", "tone", "code", "restrictions")) {
                if (root.hasNonNull(field) && !root.get(field).asString().isBlank()) {
                    result.put(field, root.get(field).asString().strip());
                }
            }
        } catch (RuntimeException ignored) {}
        return result;
    }
}
