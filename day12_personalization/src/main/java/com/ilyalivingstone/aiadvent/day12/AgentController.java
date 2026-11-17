package com.ilyalivingstone.aiadvent.day12;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final ScenarioService scenarioService;

    public AgentController(AgentService agentService, ScenarioService scenarioService) {
        this.agentService = agentService;
        this.scenarioService = scenarioService;
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
                "compressionEnabled", agentService.compressionEnabled(null),
                "keepLast", CompressionService.KEEP_LAST,
                "summaryBatch", CompressionService.SUMMARY_BATCH,
                "modelContextLimit", agentService.modelContextLimit()
        );
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        return agentService.chat(request.sessionId(), request.message());
    }

    @GetMapping("/sessions")
    public java.util.List<SessionSummary> sessions() {
        return agentService.sessionSummaries();
    }

    @PostMapping("/sessions")
    public Map<String, String> createSession() {
        return Map.of("sessionId", agentService.createSession());
    }

    @DeleteMapping("/sessions/{id}")
    public Map<String, String> deleteSession(@org.springframework.web.bind.annotation.PathVariable String id) {
        agentService.deleteSession(id);
        return Map.of("status", "deleted");
    }

    @GetMapping("/history")
    public Map<String, Object> history(@org.springframework.web.bind.annotation.RequestParam String sessionId) {
        return Map.of(
                "sessionId", sessionId,
                "messages", agentService.history(sessionId),
                "summary", agentService.summary(sessionId),
                "compressionEnabled", agentService.compressionEnabled(sessionId),
                "tokenStats", agentService.tokenStats(sessionId),
                "strategy", agentService.strategy(sessionId),
                "facts", agentService.facts(sessionId)
        );
    }

    @GetMapping("/profiles")
    public java.util.List<UserProfile> profiles() {
        return agentService.profiles();
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(@org.springframework.web.bind.annotation.RequestParam String sessionId) {
        UserProfile profile = agentService.profile(sessionId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("profile", profile);
        result.put("enabled", agentService.profileEnabled(sessionId));
        return result;
    }

    @PostMapping("/profile")
    public Map<String, String> setProfile(@RequestBody ProfileRequest request) {
        agentService.setProfile(request.sessionId(), request.profileId());
        return Map.of("status", "updated");
    }

    @DeleteMapping("/profiles/{id}")
    public Map<String, String> deleteProfile(@org.springframework.web.bind.annotation.PathVariable String id) {
        agentService.deleteProfile(id);
        return Map.of("status", "deleted");
    }

    @PostMapping("/profile/enabled")
    public Map<String, String> profileEnabled(@RequestBody ProfileEnabledRequest request) {
        agentService.setProfileEnabled(request.sessionId(), request.enabled());
        return Map.of("status", "updated");
    }

    @PostMapping("/profile/save")
    public Map<String, String> saveProfile(@RequestBody UserProfile profile) {
        agentService.saveProfile(profile);
        return Map.of("status", "saved");
    }

    @GetMapping("/memory")
    public Map<String, Object> memory(@org.springframework.web.bind.annotation.RequestParam String sessionId) {
        return agentService.memoryView(sessionId);
    }

    @PostMapping("/memory/entry")
    public Map<String, String> putMemory(@RequestBody MemoryEntryRequest request) {
        agentService.putMemory(request.sessionId(), request.layer(), request.key(), request.value());
        return Map.of("status", "saved");
    }

    @DeleteMapping("/memory/entry")
    public Map<String, String> removeMemory(@org.springframework.web.bind.annotation.RequestParam String sessionId,
                                             @org.springframework.web.bind.annotation.RequestParam MemoryLayer layer,
                                             @org.springframework.web.bind.annotation.RequestParam String key) {
        agentService.removeMemory(sessionId, layer, key);
        return Map.of("status", "removed");
    }

    @GetMapping("/memory/rules")
    public Map<String, String> memoryRules() {
        return agentService.memoryRules();
    }

    @PostMapping("/memory/rules")
    public Map<String, String> saveMemoryRules(@RequestBody MemoryRulesRequest request) {
        agentService.saveMemoryRules(request.rules());
        return Map.of("status", "saved");
    }

    @PostMapping("/memory/toggle")
    public Map<String, String> toggleMemory(@RequestBody MemoryToggleRequest request) {
        agentService.toggleMemory(request.sessionId(), request.layer(), request.enabled());
        return Map.of("status", "updated");
    }

    @PostMapping("/memory/clear")
    public Map<String, String> clearMemory(@RequestBody MemoryClearRequest request) {
        agentService.clearMemory(request.sessionId(), request.layer());
        return Map.of("status", "cleared");
    }

    @PostMapping("/strategy")
    public Map<String, String> strategy(@RequestBody StrategyRequest request) {
        agentService.strategy(request.sessionId(), request.strategy());
        return Map.of("strategy", request.strategy().name());
    }

    @PostMapping("/sessions/{id}/branch")
    public Map<String, String> branch(@org.springframework.web.bind.annotation.PathVariable String id,
                                      @RequestBody BranchRequest request) {
        return Map.of("sessionId", agentService.branch(id, request.messageIndex()));
    }

    @PostMapping("/scenario/next")
    public Map<String, String> scenarioNext(@RequestBody ScenarioNextRequest request) {
        return Map.of("message", scenarioService.nextMessage(request.sessionId(), request.detail()));
    }

    @PostMapping("/compression")
    public Map<String, Object> compression(@RequestBody CompressionRequest request) {
        agentService.setCompressionEnabled(request.sessionId(), request.enabled());
        return Map.of("compressionEnabled", request.enabled());
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

    public record CompressionRequest(String sessionId, boolean enabled) {
    }

    public record ScenarioNextRequest(String sessionId, String detail) {
    }

    public record StrategyRequest(String sessionId, ContextStrategy strategy) {
    }

    public record MemoryEntryRequest(String sessionId, MemoryLayer layer, String key, String value) {
    }

    public record MemoryClearRequest(String sessionId, MemoryLayer layer) {
    }

    public record MemoryToggleRequest(String sessionId, MemoryLayer layer, boolean enabled) {
    }

    public record MemoryRulesRequest(Map<String, String> rules) {
    }

    public record ProfileRequest(String sessionId, String profileId) {
    }

    public record ProfileEnabledRequest(String sessionId, boolean enabled) {
    }

    public record BranchRequest(int messageIndex) {
    }
}
