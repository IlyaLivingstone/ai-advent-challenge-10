package com.ilyalivingstone.aiadvent.day13;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Понятные JSON-ошибки конечного автомата: 409 для запрещённых переходов и
 * 400 для неизвестного этапа. Состояние сессии при этом не изменяется.
 */
@RestControllerAdvice
public class TaskStateExceptionHandler {

    @ExceptionHandler(TaskStateException.class)
    public ResponseEntity<Map<String, Object>> handle(TaskStateException error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error.getMessage());
        body.put("action", error.action());
        body.put("from", error.from() == null ? null : error.from().code());
        body.put("to", error.to() == null ? null : error.to().code());
        body.put("allowedTransitions", error.allowed().stream().map(TaskStage::code).toList());
        body.put("stateChanged", false);
        return ResponseEntity.status(error.statusCode()).body(body);
    }
}
