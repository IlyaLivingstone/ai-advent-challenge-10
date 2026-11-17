package com.ilyalivingstone.aiadvent.day12;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class FactService {
    private final AgentLlmClient client;
    private final JsonMapper jsonMapper;

    public FactService(AgentLlmClient client, JsonMapper jsonMapper) {
        this.client = client;
        this.jsonMapper = jsonMapper;
    }

    public void update(AgentSession session, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return;
        try {
            StringBuilder existing = new StringBuilder();
            session.facts().forEach((key, value) -> existing.append(key).append(": ").append(value).append('\n'));
            String prompt = """
                    Обнови key-value факты о проекте по новому сообщению пользователя.
                    Сохрани старые факты, добавь/исправь только явно названные детали.
                    Верни только JSON-объект без markdown: {"ключ":"значение"}.
                    Старые факты:
                    %s
                    Новое сообщение:
                    %s
                    """.formatted(existing, userMessage);
            var turn = client.chat(List.of(
                    Map.of("role", "system", "content", "Ты извлекаешь устойчивые факты из диалога."),
                    Map.of("role", "user", "content", prompt)
            ), List.of());
            String content = turn.reply().content();
            if (content == null || content.isBlank()) return;
            content = content.replace("```json", "").replace("```", "").strip();
            JsonNode root = jsonMapper.readTree(content);
            if (root.isObject()) {
                root.properties().forEach(entry -> session.facts().put(entry.getKey(), entry.getValue().asString()));
            }
        } catch (RuntimeException ignored) {
            // факты не должны ломать основной запрос
        }
    }
}
