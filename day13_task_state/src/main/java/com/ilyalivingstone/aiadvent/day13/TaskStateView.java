package com.ilyalivingstone.aiadvent.day13;

import java.util.List;

/**
 * Ответ API по состоянию задачи: снимок состояния, разрешённые переходы и журнал изменений.
 */
public record TaskStateView(
        String sessionId,
        TaskState state,
        List<String> allowedTransitions,
        List<TaskStateEvent> history
) {
}
