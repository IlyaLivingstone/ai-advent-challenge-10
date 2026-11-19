package com.ilyalivingstone.aiadvent.day13;

import java.time.Instant;

/**
 * Снимок состояния задачи в сессии: этап, текущий шаг, ожидаемое действие и признак паузы.
 * Состояние неизменяемо — любое изменение создаёт новый экземпляр через {@link TaskStateMachine}.
 */
public record TaskState(
        TaskStage stage,
        String currentStep,
        String expectedAction,
        boolean paused,
        Instant updatedAt,
        String title,
        String goal,
        boolean planApproved
) {
    public TaskState(TaskStage stage, String currentStep, String expectedAction, boolean paused, Instant updatedAt,
                     String title, String goal) {
        this(stage, currentStep, expectedAction, paused, updatedAt, title, goal, false);
    }
    public TaskState(TaskStage stage, String currentStep, String expectedAction, boolean paused, Instant updatedAt) {
        this(stage, currentStep, expectedAction, paused, updatedAt, "", "", false);
    }

    public TaskState {
        if (stage == null) {
            stage = TaskStage.PLANNING;
        }
        if (currentStep == null || currentStep.isBlank()) {
            currentStep = stage.defaultStep();
        } else {
            currentStep = currentStep.strip();
        }
        if (expectedAction == null || expectedAction.isBlank()) {
            expectedAction = stage.expectedAction();
        } else {
            expectedAction = expectedAction.strip();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (title == null) {
            title = "";
        }
        if (goal == null) {
            goal = "";
        }
    }

    public TaskState withTask(String title, String goal) {
        return new TaskState(stage, currentStep, expectedAction, paused, Instant.now(),
                title == null ? "" : title.strip(), goal == null ? "" : goal.strip(), planApproved);
    }

    public TaskState withPlanApproved(boolean approved) {
        return new TaskState(stage, currentStep, expectedAction, paused, Instant.now(), title, goal, approved);
    }

    public static TaskState initial() {
        return forStage(TaskStage.PLANNING);
    }

    public static TaskState forStage(TaskStage stage) {
        return new TaskState(stage, stage.defaultStep(), stage.expectedAction(), false, Instant.now());
    }
}
