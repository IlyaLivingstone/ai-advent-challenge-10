package com.ilyalivingstone.aiadvent.day13;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TaskStateMachineTest {

    private final TaskStateMachine machine = new TaskStateMachine();

    @Test
    void startsInPlanningWithDefaults() {
        TaskState state = machine.initial();
        assertThat(state.stage()).isEqualTo(TaskStage.PLANNING);
        assertThat(state.paused()).isFalse();
        assertThat(state.currentStep()).isNotBlank();
        assertThat(state.expectedAction()).isNotBlank();
        assertThat(machine.allowedTargets(TaskStage.PLANNING)).containsExactly(TaskStage.EXECUTION);
    }

    @Test
    void allowsOnlyForwardChain() {
        TaskState state = machine.initial();
        state = machine.transition(state, TaskStage.EXECUTION, null);
        assertThat(state.stage()).isEqualTo(TaskStage.EXECUTION);
        state = machine.transition(state, TaskStage.VALIDATION, "Запустить mvn test");
        assertThat(state.stage()).isEqualTo(TaskStage.VALIDATION);
        assertThat(state.currentStep()).isEqualTo("Запустить mvn test");
        state = machine.transition(state, TaskStage.DONE, null);
        assertThat(state.stage()).isEqualTo(TaskStage.DONE);
        assertThat(state.paused()).isFalse();
        assertThat(machine.allowedTargets(TaskStage.DONE)).isEmpty();
    }

    @Test
    void rejectsEveryTransitionOutsideTheChain() {
        for (TaskStage from : TaskStage.values()) {
            for (TaskStage to : TaskStage.values()) {
                TaskState base = TaskState.forStage(from);
                if (from != to && machine.allowedTargets(from).contains(to)) {
                    assertThat(machine.transition(base, to, null).stage()).isEqualTo(to);
                } else {
                    TaskStateException error = catchTaskStateException(
                            () -> machine.transition(base, to, null));
                    assertThat(error.getMessage()).contains("Состояние не изменено");
                    assertThat(error.from()).isEqualTo(from);
                    assertThat(error.to()).isEqualTo(to);
                    assertThat(error.statusCode()).isEqualTo(409);
                    assertThat(base.stage()).isEqualTo(from);
                }
            }
        }
    }

    @Test
    void validationCanReturnToExecutionAfterFailure() {
        TaskState validation = TaskState.forStage(TaskStage.VALIDATION);
        TaskState execution = machine.transition(validation, TaskStage.EXECUTION, "Исправить ошибки");
        assertThat(execution.stage()).isEqualTo(TaskStage.EXECUTION);
        assertThat(execution.currentStep()).isEqualTo("Исправить ошибки");
    }

    @Test
    void rejectsNullTargetWithExplanation() {
        TaskStateException error = catchTaskStateException(
                () -> machine.transition(TaskState.initial(), null, null));
        assertThat(error.getMessage()).contains("Не указан целевой этап").contains("execution");
        assertThat(error.statusCode()).isEqualTo(400);
    }

    @Test
    void pauseAndResumeAvailableOnEveryActiveStage() {
        for (TaskStage stage : List.of(TaskStage.PLANNING, TaskStage.EXECUTION, TaskStage.VALIDATION)) {
            TaskState state = machine.pause(TaskState.forStage(stage));
            assertThat(state.paused()).isTrue();
            assertThat(state.stage()).isEqualTo(stage);
            TaskState resumed = machine.resume(state);
            assertThat(resumed.paused()).isFalse();
            assertThat(resumed.stage()).isEqualTo(stage);
        }
    }

    @Test
    void pauseRejectedOnDone() {
        TaskStateException error = catchTaskStateException(() -> machine.pause(TaskState.forStage(TaskStage.DONE)));
        assertThat(error.getMessage()).contains("Пауза недоступна").contains("Состояние не изменено");
        assertThat(error.action()).isEqualTo("pause");
        assertThat(error.statusCode()).isEqualTo(409);
    }

    @Test
    void doublePauseRejected() {
        TaskState paused = machine.pause(TaskState.initial());
        TaskStateException error = catchTaskStateException(() -> machine.pause(paused));
        assertThat(error.getMessage()).contains("уже на паузе");
    }

    @Test
    void resumeWithoutPauseRejected() {
        TaskStateException error = catchTaskStateException(() -> machine.resume(TaskState.initial()));
        assertThat(error.getMessage()).contains("не на паузе");
        assertThat(error.action()).isEqualTo("resume");
    }

    @Test
    void transitionWhilePausedRejectedAndStateUntouched() {
        TaskState paused = machine.pause(TaskState.forStage(TaskStage.EXECUTION));
        TaskStateException error = catchTaskStateException(
                () -> machine.transition(paused, TaskStage.VALIDATION, null));
        assertThat(error.getMessage()).contains("пока задача на паузе").contains("resume");
        assertThat(error.from()).isEqualTo(TaskStage.EXECUTION);
        assertThat(paused.stage()).isEqualTo(TaskStage.EXECUTION);
        assertThat(paused.paused()).isTrue();
    }

    @Test
    void stateIsImmutable() {
        TaskState initial = TaskState.initial();
        machine.transition(initial, TaskStage.EXECUTION, "Новый шаг");
        machine.pause(initial);
        assertThat(initial.stage()).isEqualTo(TaskStage.PLANNING);
        assertThat(initial.paused()).isFalse();
        assertThat(initial.currentStep()).isNotEqualTo("Новый шаг");
    }

    @Test
    void unknownStageCodeRejectedWithHint() {
        TaskStateException error = catchTaskStateException(() -> TaskStage.fromCode("review"));
        assertThat(error.getMessage())
                .contains("Неизвестный этап")
                .contains("planning")
                .contains("validation");
        assertThat(error.statusCode()).isEqualTo(400);
    }

    @Test
    void describesMachineForApi() {
        var description = machine.describe();
        assertThat(description.get("initialStage")).isEqualTo("planning");
        assertThat((List<?>) description.get("stages")).hasSize(4);
        assertThat((List<?>) description.get("transitions")).hasSize(4);
        @SuppressWarnings("unchecked")
        List<String> pauseAllowedOn = (List<String>) description.get("pauseAllowedOn");
        assertThat(pauseAllowedOn).containsExactly("planning", "execution", "validation");
    }

    private TaskStateException catchTaskStateException(Runnable action) {
        try {
            action.run();
        } catch (TaskStateException error) {
            return error;
        }
        throw new AssertionError("Ожидалась ошибка TaskStateException");
    }
}
