package com.ilyalivingstone.aiadvent.day08;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return Map.of(
                "tools", agentService.tools(),
                "examples", List.of(
                        "Объясни, что такое AI-агент",
                        "Который час в Москве?",
                        "Переведи 5000 рублей в доллары, затем увеличь результат на 10%."
                ),
                "maxRounds", 6,
                "contextLimit", agentService.contextLimit(),
                "gateEnabled", agentService.gateEnabled(),
                "modelContextLimit", agentService.modelContextLimit()
        );
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        return agentService.chat(request.sessionId(), request.message());
    }

    @GetMapping("/history")
    public Map<String, Object> history(@org.springframework.web.bind.annotation.RequestParam String sessionId) {
        return Map.of("sessionId", sessionId, "messages", agentService.history(sessionId));
    }

    @PostMapping("/gate")
    public Map<String, Object> gate(@RequestBody GateRequest request) {
        boolean enabled = agentService.setGateEnabled(request.enabled());
        return Map.of("gateEnabled", enabled, "contextLimit", agentService.contextLimit());
    }

    @PostMapping(value = "/stress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stress(@RequestBody StressRequest request) {
        return agentService.chat(request.sessionId(), SyntheticStory.generate());
    }

    @PostMapping("/reset")
    public Map<String, String> reset(@RequestBody ResetRequest request) {
        agentService.reset(request.sessionId());
        return Map.of("status", "reset");
    }

    public record ChatRequest(String sessionId, String message) {
    }

    public record ResetRequest(String sessionId) {
    }

    public record GateRequest(boolean enabled) {
    }

    public record StressRequest(String sessionId) {
    }
}
