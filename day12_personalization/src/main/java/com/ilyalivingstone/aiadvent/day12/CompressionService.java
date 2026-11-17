package com.ilyalivingstone.aiadvent.day12;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CompressionService {

    public static final int KEEP_LAST = 8;
    public static final int SUMMARY_BATCH = 10;
    private final AgentLlmClient client;

    public CompressionService(AgentLlmClient client) {
        this.client = client;
    }

    public List<Map<String, Object>> context(AgentSession session) {
        List<Map<String, Object>> messages = session.messages();
        if (!session.compressionEnabled() || messages.size() <= KEEP_LAST + 1) {
            return sanitize(messages);
        }
        summarizeOld(session);
        List<Map<String, Object>> context = new ArrayList<>();
        int systemOffset = !messages.isEmpty() && "system".equals(messages.getFirst().get("role")) ? 1 : 0;
        if (systemOffset == 1) {
            context.add(messages.getFirst());
        }
        if (!session.summary().isBlank()) {
            context.add(Map.of(
                    "role", "system",
                    "content", "Краткое содержание предыдущей части диалога:\n" + session.summary()
            ));
        }
        int start = Math.max(systemOffset + session.summarizedCount(), messages.size() - KEEP_LAST);
        while (start < messages.size() && !isUser(messages.get(start))) {
            start++;
        }
        if (start >= messages.size()) {
            int lastUser = messages.size() - 1;
            while (lastUser > systemOffset && !isUser(messages.get(lastUser))) {
                lastUser--;
            }
            start = isUser(messages.get(lastUser)) ? lastUser : systemOffset;
        }
        context.addAll(messages.subList(start, messages.size()));
        return context;
    }

    public boolean summarizeOld(AgentSession session) {
        if (!session.compressionEnabled()) {
            return false;
        }
        List<Map<String, Object>> messages = session.messages();
        int systemOffset = !messages.isEmpty() && "system".equals(messages.getFirst().get("role")) ? 1 : 0;
        int end = messages.size() - KEEP_LAST;
        int start = systemOffset + session.summarizedCount();
        boolean changed = false;
        while (end - start >= SUMMARY_BATCH) {
            int chunkEnd = Math.min(start + SUMMARY_BATCH, end);
            while (chunkEnd < end && !isUser(messages.get(chunkEnd))) {
                chunkEnd++;
            }
            session.summary(summarizeChunk(session.summary(), messages.subList(start, chunkEnd)),
                    session.summarizedCount() + (chunkEnd - start));
            start = chunkEnd;
            changed = true;
        }
        return changed;
    }

    private List<Map<String, Object>> sanitize(List<Map<String, Object>> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        java.util.Set<String> calls = new java.util.HashSet<>();
        for (Map<String, Object> message : messages) {
            String role = String.valueOf(message.get("role"));
            if ("assistant".equals(role) && message.get("tool_calls") instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> call) {
                        Object id = call.get("id");
                        if (id != null) calls.add(String.valueOf(id));
                    }
                }
                result.add(message);
            } else if ("tool".equals(role)) {
                Object id = message.get("tool_call_id");
                if (id != null && calls.contains(String.valueOf(id))) {
                    result.add(message);
                }
            } else {
                result.add(message);
            }
        }
        return result;
    }

    private boolean isUser(Map<String, Object> message) {
        return "user".equals(message.get("role"));
    }

    private String summarizeChunk(String existing, List<Map<String, Object>> chunk) {
        StringBuilder transcript = new StringBuilder();
        for (Map<String, Object> message : chunk) {
            String role = String.valueOf(message.get("role"));
            String content = String.valueOf(message.getOrDefault("content", ""));
            if (content.isBlank()) continue;
            if (content.length() > 900) content = content.substring(0, 900) + "…";
            transcript.append(role).append(": ").append(content).append('\n');
        }
        String prompt = """
                Сожми старую часть диалога в 5–8 коротких пунктов, максимум 1200 символов.
                Сохрани дословно все конкретные детали: имена, сроки, бюджеты, ограничения,
                принятые решения и договорённости. Не добавляй новые факты. Не пересказывай
                служебные инструкции. Верни только готовое summary без вступления.

                Уже есть summary:
                %s

                Новые старые сообщения:
                %s
                """.formatted(existing == null ? "нет" : existing, transcript);
        try {
            var turn = client.chat(List.of(
                    Map.of("role", "system", "content", "Ты сжимаешь историю диалога, сохраняя важные детали."),
                    Map.of("role", "user", "content", prompt)
            ), List.of());
            String content = turn.reply().content();
            if (content != null && !content.isBlank()) {
                String result = content.strip();
                return result.length() > 1800 ? result.substring(0, 1800) : result;
            }
        } catch (RuntimeException ignored) {
            // Fallback ниже: локальная выжимка, если суммаризатор недоступен.
        }
        return fallbackSummary(existing, chunk);
    }

    private String fallbackSummary(String existing, List<Map<String, Object>> chunk) {
        StringBuilder added = new StringBuilder();
        for (Map<String, Object> message : chunk) {
            String role = String.valueOf(message.get("role"));
            String content = String.valueOf(message.getOrDefault("content", ""));
            if (content.isBlank()) continue;
            if (content.length() > 300) content = content.substring(0, 300) + "…";
            added.append("- ").append(role).append(": ").append(content.strip()).append('\n');
        }
        String result = (existing == null ? "" : existing) + added;
        return result.length() > 1800 ? result.substring(result.length() - 1800) : result;
    }
}
