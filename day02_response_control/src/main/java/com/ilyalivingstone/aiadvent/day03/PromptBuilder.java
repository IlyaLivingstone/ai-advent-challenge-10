package com.ilyalivingstone.aiadvent.day03;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    private static final String FINAL_INSTRUCTION = """
            В самом конце ответа добавь ровно одну строку:
            FINAL: <только краткий ответ>
            """.strip();

    public String promptFor(ReasoningMethod method, ReasoningTask task) {
        return switch (method) {
            case DIRECT -> direct(task);
            case STEP_BY_STEP -> stepByStep(task);
            case EXPERT_PANEL -> expertPanel(task);
            case META_PROMPT -> throw new IllegalArgumentException(
                    "Для META_PROMPT нужны два этапа"
            );
        };
    }

    public String metaPromptStage(ReasoningTask task) {
        return """
                Ты методист по решению задач. Составь подробный промпт для другой языковой модели,
                который поможет ей безошибочно решить задачу ниже. Не решай задачу и не сообщай ответ.
                Верни только текст промпта, без пояснений.

                Задача:
                %s
                """.formatted(task.question()).strip();
    }

    public String fromGeneratedPrompt(String generatedPrompt, ReasoningTask task) {
        return """
                Используй следующий промпт для решения задачи:
                %s

                Задача:
                %s

                %s
                """.formatted(generatedPrompt.strip(), task.question(), FINAL_INSTRUCTION).strip();
    }

    private String direct(ReasoningTask task) {
        return """
                %s

                %s
                """.formatted(task.question(), FINAL_INSTRUCTION).strip();
    }

    private String stepByStep(ReasoningTask task) {
        return """
                %s

                Решай задачу пошагово. Покажи ход рассуждения, затем дай итог.

                %s
                """.formatted(task.question(), FINAL_INSTRUCTION).strip();
    }

    private String expertPanel(ReasoningTask task) {
        return """
                Ты группа из трёх экспертов:
                - аналитик: формализует условие и проверяет логику;
                - инженер: строит вычисление или алгоритм;
                - критик: ищет ошибки и альтернативные трактовки.

                Сначала каждый эксперт независимо предлагает своё решение. Затем критик проверяет
                расхождения, и группа формирует один согласованный итог.

                Задача:
                %s

                %s
                """.formatted(task.question(), FINAL_INSTRUCTION).strip();
    }
}
