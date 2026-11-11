package com.ilyalivingstone.aiadvent.day07;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class RestartController {

    private final RestartService restartService;

    public RestartController(RestartService restartService) {
        this.restartService = restartService;
    }

    @PostMapping("/restart")
    public ResponseEntity<Map<String, String>> restart() {
        restartService.restart();
        return ResponseEntity.accepted().body(Map.of(
                "status", "restarting",
                "message", "Сервис будет перезапущен через 2 секунды"
        ));
    }
}
