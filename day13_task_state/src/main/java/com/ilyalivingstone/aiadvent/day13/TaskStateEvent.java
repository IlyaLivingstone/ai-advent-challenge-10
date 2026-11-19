package com.ilyalivingstone.aiadvent.day13;

import java.time.Instant;

/**
 * Запись журнала изменений состояния задачи: действие transition / pause / resume.
 */
public record TaskStateEvent(
        Instant at,
        String action,
        TaskStage from,
        TaskStage to,
        String currentStep
) {
}
