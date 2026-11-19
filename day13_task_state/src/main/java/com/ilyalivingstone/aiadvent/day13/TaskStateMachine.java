package com.ilyalivingstone.aiadvent.day13;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Кодовый конечный автомат состояния задачи.
 *
 * <p>Разрешены только переходы planning → execution → validation → done.
 * Пауза и продолжение доступны на любом активном этапе (planning, execution, validation).
 * Любая недопустимая операция отклоняется {@link TaskStateException} до изменения состояния.</p>
 */
@Component
public class TaskStateMachine {

    private static final Map<TaskStage, List<TaskStage>> ALLOWED = allowedTransitions();
    private static final List<TaskStage> ACTIVE_STAGES = List.of(
            TaskStage.PLANNING, TaskStage.EXECUTION, TaskStage.VALIDATION
    );

    private static Map<TaskStage, List<TaskStage>> allowedTransitions() {
        Map<TaskStage, List<TaskStage>> map = new EnumMap<>(TaskStage.class);
        map.put(TaskStage.PLANNING, List.of(TaskStage.EXECUTION));
        map.put(TaskStage.EXECUTION, List.of(TaskStage.VALIDATION));
        map.put(TaskStage.VALIDATION, List.of(TaskStage.DONE, TaskStage.EXECUTION));
        map.put(TaskStage.DONE, List.of());
        return java.util.Collections.unmodifiableMap(map);
    }

    public TaskState initial() {
        return TaskState.initial();
    }

    public List<TaskStage> allowedTargets(TaskStage stage) {
        if (stage == null) {
            return ALLOWED.get(TaskStage.PLANNING);
        }
        return ALLOWED.getOrDefault(stage, List.of());
    }

    public List<TaskStage> activeStages() {
        return ACTIVE_STAGES;
    }

    public TaskState transition(TaskState current, TaskStage target, String currentStep) {
        TaskState state = current == null ? TaskState.initial() : current;
        List<TaskStage> allowed = allowedTargets(state.stage());
        if (target == null) {
            throw TaskStateException.badRequest(
                    "transition", state.stage(), null, allowed,
                    "Не указан целевой этап. Текущий этап: " + state.stage().code()
                            + ". Разрешённый переход: " + describeTargets(allowed) + "."
            );
        }
        if (state.paused()) {
            throw TaskStateException.conflict(
                    "transition", state.stage(), target, allowed,
                    "Нельзя менять этап, пока задача на паузе: сначала продолжите выполнение (resume). "
                            + "Состояние не изменено."
            );
        }
        if (target == state.stage()) {
            throw TaskStateException.conflict(
                    "transition", state.stage(), target, allowed,
                    "Задача уже находится на этапе " + state.stage().code() + ". Состояние не изменено."
            );
        }
        if (!allowed.contains(target)) {
            throw TaskStateException.conflict(
                    "transition", state.stage(), target, allowed,
                    "Недопустимый переход состояния задачи: " + state.stage().code() + " → " + target.code()
                            + ". Текущий этап: " + state.stage().code()
                            + ". Разрешённый переход: " + describeTargets(allowed)
                            + ". Состояние не изменено."
            );
        }
        String step = currentStep == null || currentStep.isBlank() ? target.defaultStep() : currentStep.strip();
        return new TaskState(target, step, target.expectedAction(), false, Instant.now(),
                state.title(), state.goal(), state.planApproved() || state.stage() == TaskStage.PLANNING);
    }

    public TaskState pause(TaskState current) {
        TaskState state = current == null ? TaskState.initial() : current;
        if (state.stage().isDone()) {
            throw TaskStateException.conflict(
                    "pause", state.stage(), state.stage(), List.of(),
                    "Пауза недоступна: этап done завершает задачу. Активные этапы: planning, execution, validation. "
                            + "Состояние не изменено."
            );
        }
        if (state.paused()) {
            throw TaskStateException.conflict(
                    "pause", state.stage(), state.stage(), allowedTargets(state.stage()),
                    "Задача уже на паузе. Сначала продолжите выполнение (resume). Состояние не изменено."
            );
        }
        return new TaskState(state.stage(), state.currentStep(), state.expectedAction(), true, Instant.now(),
                state.title(), state.goal(), state.planApproved());
    }

    public TaskState resume(TaskState current) {
        TaskState state = current == null ? TaskState.initial() : current;
        if (!state.paused()) {
            throw TaskStateException.conflict(
                    "resume", state.stage(), state.stage(), allowedTargets(state.stage()),
                    "Задача не на паузе: продолжение недоступно. Состояние не изменено."
            );
        }
        return new TaskState(state.stage(), state.currentStep(), state.expectedAction(), false, Instant.now(),
                state.title(), state.goal(), state.planApproved());
    }

    /** Описание автомата для API: этапы, разрешённые переходы и действия. */
    public Map<String, Object> describe() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> stages = new ArrayList<>();
        for (TaskStage stage : TaskStage.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stage", stage.code());
            item.put("label", stage.label());
            item.put("active", stage.active());
            item.put("defaultStep", stage.defaultStep());
            item.put("expectedAction", stage.expectedAction());
            item.put("allowedTransitions", allowedTargets(stage).stream().map(TaskStage::code).toList());
            stages.add(item);
        }
        List<Map<String, Object>> transitions = new ArrayList<>();
        for (TaskStage from : TaskStage.values()) {
            for (TaskStage to : allowedTargets(from)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("from", from.code());
                item.put("to", to.code());
                transitions.add(item);
            }
        }
        result.put("stages", stages);
        result.put("transitions", transitions);
        result.put("pauseAllowedOn", ACTIVE_STAGES.stream().map(TaskStage::code).toList());
        result.put("resumeAllowedOn", ACTIVE_STAGES.stream().map(TaskStage::code).toList());
        result.put("initialStage", TaskStage.PLANNING.code());
        return result;
    }

    private String describeTargets(List<TaskStage> targets) {
        if (targets.isEmpty()) {
            return "нет (этап завершает задачу)";
        }
        return targets.stream().map(TaskStage::code).collect(java.util.stream.Collectors.joining(", "));
    }
}
