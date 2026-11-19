package com.ilyalivingstone.aiadvent.day13;

import java.util.List;

/**
 * Ошибка работы конечного автомата. Бросается до изменения состояния:
 * сессия и файл сессии остаются нетронутыми.
 */
public class TaskStateException extends RuntimeException {

    private final String action;
    private final TaskStage from;
    private final TaskStage to;
    private final List<TaskStage> allowed;
    private final int statusCode;

    private TaskStateException(String action, TaskStage from, TaskStage to, List<TaskStage> allowed,
                               String message, int statusCode) {
        super(message);
        this.action = action;
        this.from = from;
        this.to = to;
        this.allowed = allowed == null ? List.of() : List.copyOf(allowed);
        this.statusCode = statusCode;
    }

    public static TaskStateException conflict(String action, TaskStage from, TaskStage to,
                                              List<TaskStage> allowed, String message) {
        return new TaskStateException(action, from, to, allowed, message, 409);
    }

    public static TaskStateException badRequest(String action, TaskStage from, TaskStage to,
                                               List<TaskStage> allowed, String message) {
        return new TaskStateException(action, from, to, allowed, message, 400);
    }

    public String action() {
        return action;
    }

    public TaskStage from() {
        return from;
    }

    public TaskStage to() {
        return to;
    }

    public List<TaskStage> allowed() {
        return allowed;
    }

    public int statusCode() {
        return statusCode;
    }
}
