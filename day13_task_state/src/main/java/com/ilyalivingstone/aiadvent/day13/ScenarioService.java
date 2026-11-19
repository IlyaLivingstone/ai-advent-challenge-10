package com.ilyalivingstone.aiadvent.day13;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ScenarioService {

    private final AgentLlmClient client;
    private final AgentService agentService;

    public ScenarioService(AgentLlmClient client, AgentService agentService) {
        this.client = client;
        this.agentService = agentService;
    }

    public String nextMessage(String sessionId, String detail) {
        List<Map<String, Object>> history = agentService.history(sessionId);
        StringBuilder context = new StringBuilder();
        boolean firstMessage = history.stream().noneMatch(message -> "user".equals(message.get("role")));
        int from = Math.max(0, history.size() - 8);
        for (int i = from; i < history.size(); i++) {
            Map<String, Object> message = history.get(i);
            Object role = message.get("role");
            Object content = message.get("content");
            if (content == null || String.valueOf(content).isBlank()) continue;
            context.append(role).append(": ").append(String.valueOf(content)).append("\n");
        }
        String firstInstruction = firstMessage
                ? "Это начало диалога: начни его естественно, без отсылок к прошлому и без слов «тогда», «продолжая»."
                : "Это продолжение диалога: опирайся на последний ответ ассистента, но не начинай со слов «тогда», если они звучат как продолжение старой мысли.";
        String prompt = """
                Ты участник естественного диалога о разработке приложения.
                Продолжи разговор одной короткой репликой пользователя.
                Реплика должна органично вводить новую деталь: %s.
                %s
                Не используй слово «запомни», не объясняй задание, не пиши от лица ассистента.
                Верни только текст реплики пользователя.

                Последние сообщения:
                %s
                """.formatted(detail, firstInstruction, context.toString().strip());
        var turn = client.chat(List.of(
                Map.of("role", "system", "content", "Ты генерируешь только реплики пользователя для сценарного диалога."),
                Map.of("role", "user", "content", prompt)
        ), List.of());
        String content = turn.reply().content();
        if (content == null || content.isBlank()) {
            return "Давай уточним: " + detail + ".";
        }
        return content.strip();
    }
}
