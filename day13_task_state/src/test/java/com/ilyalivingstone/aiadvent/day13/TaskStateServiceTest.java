package com.ilyalivingstone.aiadvent.day13;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.agent.storage-dir=target/test-day13-task-state-sessions",
        "app.profile.dir=target/test-day13-task-state-profiles",
        "app.memory.dir=target/test-day13-task-state-memory"
})
class TaskStateServiceTest {

    @Autowired
    private AgentService agentService;

    @Autowired
    private SessionStore store;

    @Test
    void stateIsStoredInSessionAndSurvivesPersistence() {
        String sessionId = agentService.createSession();

        TaskStateView initial = agentService.taskState(sessionId);
        assertThat(initial.state().stage()).isEqualTo(TaskStage.PLANNING);
        assertThat(initial.allowedTransitions()).containsExactly("execution");
        assertThat(store.load(sessionId).taskState().stage()).isEqualTo(TaskStage.PLANNING);

        TaskStateView executed = agentService.transitionTask(sessionId, TaskStage.EXECUTION, "Реализовать автомат");
        assertThat(executed.state().stage()).isEqualTo(TaskStage.EXECUTION);
        assertThat(executed.state().currentStep()).isEqualTo("Реализовать автомат");
        assertThat(executed.state().expectedAction()).isEqualTo(TaskStage.EXECUTION.expectedAction());

        TaskState persisted = store.load(sessionId).taskState();
        assertThat(persisted.stage()).isEqualTo(TaskStage.EXECUTION);
        assertThat(persisted.currentStep()).isEqualTo("Реализовать автомат");
        assertThat(persisted.expectedAction()).isEqualTo(TaskStage.EXECUTION.expectedAction());
        assertThat(persisted.paused()).isFalse();

        agentService.pauseTask(sessionId);
        assertThat(store.load(sessionId).taskState().paused()).isTrue();
        assertThat(store.load(sessionId).taskState().stage()).isEqualTo(TaskStage.EXECUTION);

        agentService.resumeTask(sessionId);
        assertThat(store.load(sessionId).taskState().paused()).isFalse();
        assertThat(store.load(sessionId).taskHistory())
                .extracting(TaskStateEvent::action)
                .containsExactly("transition", "pause", "resume");
    }

    @Test
    void forbiddenTransitionRejectedBeforeStateChange() {
        String sessionId = agentService.createSession();
        agentService.transitionTask(sessionId, TaskStage.EXECUTION, "Написать FSM");
        TaskState before = agentService.taskState(sessionId).state();
        int historyBefore = agentService.taskState(sessionId).history().size();

        assertThatThrownBy(() -> agentService.transitionTask(sessionId, TaskStage.DONE, null))
                .isInstanceOf(TaskStateException.class)
                .hasMessageContaining("Недопустимый переход")
                .hasMessageContaining("execution → done")
                .hasMessageContaining("Состояние не изменено");

        TaskStateView after = agentService.taskState(sessionId);
        assertThat(after.state()).isEqualTo(before);
        assertThat(after.state().stage()).isEqualTo(TaskStage.EXECUTION);
        assertThat(after.history()).hasSize(historyBefore);
        assertThat(store.load(sessionId).taskState()).isEqualTo(before);
        assertThat(store.load(sessionId).taskState().stage()).isEqualTo(TaskStage.EXECUTION);
    }

    @Test
    void pauseBlocksTransitionsUntilResume() {
        String sessionId = agentService.createSession();
        agentService.transitionTask(sessionId, TaskStage.EXECUTION, null);
        agentService.pauseTask(sessionId);

        assertThatThrownBy(() -> agentService.transitionTask(sessionId, TaskStage.VALIDATION, null))
                .isInstanceOf(TaskStateException.class)
                .hasMessageContaining("на паузе");
        assertThat(agentService.taskState(sessionId).state().paused()).isTrue();
        assertThat(agentService.taskState(sessionId).state().stage()).isEqualTo(TaskStage.EXECUTION);
        assertThat(store.load(sessionId).taskState().stage()).isEqualTo(TaskStage.EXECUTION);
        assertThat(store.load(sessionId).taskState().paused()).isTrue();

        agentService.resumeTask(sessionId);
        agentService.transitionTask(sessionId, TaskStage.VALIDATION, null);
        assertThat(store.load(sessionId).taskState().stage()).isEqualTo(TaskStage.VALIDATION);
    }

    @Test
    void doneStageIsTerminal() {
        String sessionId = agentService.createSession();
        agentService.transitionTask(sessionId, TaskStage.EXECUTION, null);
        agentService.transitionTask(sessionId, TaskStage.VALIDATION, null);
        agentService.transitionTask(sessionId, TaskStage.DONE, null);

        assertThatThrownBy(() -> agentService.pauseTask(sessionId))
                .isInstanceOf(TaskStateException.class)
                .hasMessageContaining("Пауза недоступна");
        assertThatThrownBy(() -> agentService.transitionTask(sessionId, TaskStage.PLANNING, null))
                .isInstanceOf(TaskStateException.class)
                .hasMessageContaining("Недопустимый переход");
        assertThat(store.load(sessionId).taskState().stage()).isEqualTo(TaskStage.DONE);
    }

    @Test
    void promptContainsDedicatedTaskStateBlock() {
        String sessionId = agentService.createSession();
        agentService.transitionTask(sessionId, TaskStage.EXECUTION, "Написать FSM");

        String block = agentService.taskPromptBlock(sessionId);
        assertThat(block)
                .contains("Состояние задачи (конечный автомат)")
                .contains("- этап: execution — Выполнение")
                .contains("- текущий шаг: Написать FSM")
                .contains("- ожидаемое действие: " + TaskStage.EXECUTION.expectedAction())
                .contains("- пауза: нет")
                .contains("- следующий разрешённый этап: validation")
                .doesNotContain("Профиль пользователя");
    }

    @Test
    void pausedStateMarksPromptAsPaused() {
        String sessionId = agentService.createSession();
        agentService.transitionTask(sessionId, TaskStage.EXECUTION, null);
        agentService.pauseTask(sessionId);
        String block = agentService.taskPromptBlock(sessionId);
        assertThat(block).contains("- пауза: да").contains("resume").contains("execution");
    }

    @Test
    void memoryAndProfileArePreservedAlongsideTaskState() {
        String sessionId = agentService.createSession();
        agentService.putMemory(sessionId, MemoryLayer.WORKING, "task.goal", "FSM Day 13");
        agentService.setProfile(sessionId, "detailed");
        agentService.transitionTask(sessionId, TaskStage.EXECUTION, null);

        SessionSnapshot snapshot = store.load(sessionId);
        assertThat(snapshot.taskState().stage()).isEqualTo(TaskStage.EXECUTION);
        assertThat(snapshot.working()).containsEntry("task.goal", "FSM Day 13");
        assertThat(snapshot.profileId()).isEqualTo("detailed");
        assertThat(agentService.memoryView(sessionId).get("working")).isNotNull();
        assertThat(agentService.profile(sessionId).id()).isEqualTo("detailed");
    }

    @Test
    void branchCopiesTaskState() {
        String sessionId = agentService.createSession();
        agentService.transitionTask(sessionId, TaskStage.EXECUTION, "Ветка: шаг");
        agentService.pauseTask(sessionId);

        String branchId = agentService.branch(sessionId, 0);
        TaskStateView branch = agentService.taskState(branchId);
        assertThat(branch.state().stage()).isEqualTo(TaskStage.EXECUTION);
        assertThat(branch.state().paused()).isTrue();
        assertThat(branch.state().currentStep()).isEqualTo("Ветка: шаг");
        assertThat(branch.history()).isNotEmpty();
        assertThat(store.load(branchId).taskState().stage()).isEqualTo(TaskStage.EXECUTION);
    }

    @Test
    void resetReturnsTaskToPlanning() {
        String sessionId = agentService.createSession();
        agentService.transitionTask(sessionId, TaskStage.EXECUTION, null);
        agentService.reset(sessionId);
        assertThat(agentService.taskState(sessionId).state().stage()).isEqualTo(TaskStage.PLANNING);
        assertThat(agentService.taskState(sessionId).history()).isEmpty();
    }

    @Test
    void promptContextKeepsProfileMemoryAndAddsTaskStateBlock() {
        String sessionId = agentService.createSession();
        agentService.putMemory(sessionId, MemoryLayer.WORKING, "task.goal", "FSM Day 13");
        agentService.transitionTask(sessionId, TaskStage.EXECUTION, "Написать FSM");

        List<Map<String, Object>> context = agentService.contextFor(agentService.agentSession(sessionId));
        List<String> systemBlocks = context.stream()
                .filter(message -> "system".equals(message.get("role")))
                .<String>map(message -> String.valueOf(message.get("content")))
                .toList();

        assertThat(systemBlocks).anyMatch(text -> text.contains("Профиль пользователя"));
        assertThat(systemBlocks).anyMatch(text -> text.contains("Рабочая память текущей задачи"));
        assertThat(systemBlocks).anyMatch(text -> text.contains("Состояние задачи (конечный автомат)"));
        assertThat(systemBlocks).anyMatch(text -> text.contains("этап: execution"));
    }

    @Test
    void sessionListDoesNotBreakWithTaskState() {
        String sessionId = agentService.createSession();
        agentService.transitionTask(sessionId, TaskStage.EXECUTION, null);
        assertThat(agentService.sessionSummaries())
                .anyMatch(summary -> summary.id().equals(sessionId));
        List<TaskStateEvent> history = agentService.taskState(sessionId).history();
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().to()).isEqualTo(TaskStage.EXECUTION);
    }
}
