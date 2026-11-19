package com.ilyalivingstone.aiadvent.day13;

/**
 * Формальные этапы задачи. Переходы между этапами разрешает только
 * {@link TaskStateMachine}; порядок объявления задаёт отображение в UI и промпт.
 */
public enum TaskStage {

    PLANNING("planning", "Планирование",
            "Сформулировать план выполнения задачи",
            "Разбей задачу на шаги, перечисли риски и предложи план. Код пока не пиши."),
    EXECUTION("execution", "Выполнение",
            "Выполнить текущий шаг плана",
            "Выполняй утверждённый план по шагам и сообщай, какой шаг закрыт."),
    VALIDATION("validation", "Проверка",
            "Проверить результат и тесты",
            "Проверь результат: тесты, сборка, соответствие требованиям. Новых фич не добавляй."),
    DONE("done", "Завершено",
            "Задача завершена",
            "Задача завершена: новых действий не требуется, подведи итог.");

    private final String code;
    private final String label;
    private final String defaultStep;
    private final String expectedAction;

    TaskStage(String code, String label, String defaultStep, String expectedAction) {
        this.code = code;
        this.label = label;
        this.defaultStep = defaultStep;
        this.expectedAction = expectedAction;
    }

    @com.fasterxml.jackson.annotation.JsonValue
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String defaultStep() {
        return defaultStep;
    }

    public String expectedAction() {
        return expectedAction;
    }

    /** Этап, на котором задача может быть поставлена на паузу. */
    public boolean active() {
        return this != DONE;
    }

    public boolean isDone() {
        return this == DONE;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static TaskStage fromCode(String code) {
        if (code != null) {
            String normalized = code.strip().toLowerCase(java.util.Locale.ROOT);
            for (TaskStage stage : values()) {
                if (stage.code.equals(normalized) || stage.name().equalsIgnoreCase(normalized)) {
                    return stage;
                }
            }
        }
        throw TaskStateException.badRequest(
                "unknown-stage",
                null,
                null,
                java.util.List.of(),
                "Неизвестный этап задачи: «" + (code == null ? "" : code) + "». "
                        + "Допустимые этапы: planning, execution, validation, done."
        );
    }
}
