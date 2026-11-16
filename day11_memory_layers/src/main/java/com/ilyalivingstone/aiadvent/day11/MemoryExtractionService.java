package com.ilyalivingstone.aiadvent.day11;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MemoryExtractionService {
    private final AgentLlmClient client;
    private final MemoryStore store;
    private final MemoryRuleStore rules;
    private final JsonMapper jsonMapper;

    public MemoryExtractionService(AgentLlmClient client, MemoryStore store,
                                   MemoryRuleStore rules, JsonMapper jsonMapper) {
        this.client = client;
        this.store = store;
        this.rules = rules;
        this.jsonMapper = jsonMapper;
    }

    public void extract(AgentSession session, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return;
        try {
            Map<String, String> existingWorking = session.working();
            Map<String, String> existingLong = store.longTerm();
            Map<String, String> rulesMap = rules.rules();
            String prompt = """
                    Разложи сообщение пользователя по двум слоям памяти.

                    WORKING — данные текущей задачи: текущая цель, релиз/этап, следующий шаг,
                    текущий артефакт, дедлайн и ограничения именно этой задачи.
                    Если в сообщении есть текущая задача — в working должна быть хотя бы одна запись.

                    LONG_TERM — устойчивые данные: имя, роль, проект, платформа, команда,
                    принятые решения, долговременные ограничения и предпочтения.
                    Не сохраняй в long_term текущий этап, следующий шаг и дедлайн конкретной задачи.

                    Правила пользователя:
                    WORKING: %s
                    LONG_TERM: %s

                    Уже сохранено:
                    WORKING:
                    %s
                    LONG_TERM:
                    %s

                    Новое сообщение:
                    %s

                    Верни строго JSON:
                    {"working": {"ключ": "значение"}, "longTerm": {"ключ": "значение"}}
                    Не повторяй неизменённые записи. Если для слоя нечего добавить — {}.
                    """.formatted(rulesMap.get("WORKING"), rulesMap.get("LONG_TERM"),
                    format(existingWorking), format(existingLong), userMessage);
            var turn = client.chat(List.of(
                    Map.of("role", "system", "content", "Ты раскладываешь информацию по слоям памяти и отвечаешь только JSON."),
                    Map.of("role", "user", "content", prompt)
            ), List.of());
            String content = turn.reply().content();
            if (content == null || content.isBlank()) return;
            content = content.replace("```json", "").replace("```", "").strip();
            JsonNode root = jsonMapper.readTree(content);
            if (!root.isObject()) return;
            Map<String, String> extractedWorking = entries(root.path("working"));
            Map<String, String> extractedLong = entries(root.path("longTerm"));
            if (session.workingEnabled() && !extractedWorking.isEmpty()) {
                session.working().putAll(extractedWorking);
            }
            if (session.longTermEnabled() && !extractedLong.isEmpty()) {
                store.merge(extractedLong);
            }
        } catch (RuntimeException ignored) {
            // извлечение памяти не должно ломать основной запрос
        }
    }

    private Map<String, String> entries(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.properties().forEach(entry -> result.put(entry.getKey(), entry.getValue().asString()));
        }
        return result;
    }

    private String format(Map<String, String> values) {
        if (values.isEmpty()) return "пусто";
        StringBuilder text = new StringBuilder();
        values.forEach((key, value) -> text.append("- ").append(key).append(": ").append(value).append('\n'));
        return text.toString();
    }
}
