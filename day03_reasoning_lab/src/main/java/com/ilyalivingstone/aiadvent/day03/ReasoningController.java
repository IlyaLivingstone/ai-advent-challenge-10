package com.ilyalivingstone.aiadvent.day03;

import com.ilyalivingstone.aiadvent.day03.llm.DeepSeekProperties;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reasoning")
public class ReasoningController {

    private final ReasoningTaskCatalog catalog;
    private final ReasoningJobService jobService;
    private final DeepSeekProperties properties;

    public ReasoningController(
            ReasoningTaskCatalog catalog,
            ReasoningJobService jobService,
            DeepSeekProperties properties
    ) {
        this.catalog = catalog;
        this.jobService = jobService;
        this.properties = properties;
    }

    @GetMapping("/meta")
    public ReasoningMeta meta() {
        List<ReasoningMeta.TaskMeta> tasks = catalog.tasks().stream()
                .map(task -> new ReasoningMeta.TaskMeta(
                        task.id(),
                        task.category(),
                        task.title(),
                        task.question(),
                        task.expectedAnswer()
                ))
                .toList();
        List<ReasoningMeta.MethodMeta> methods = Arrays.stream(ReasoningMethod.values())
                .map(method -> new ReasoningMeta.MethodMeta(method.id(), method.title()))
                .toList();
        List<ReasoningMeta.ModeMeta> modes = Arrays.stream(ReasoningMode.values())
                .map(mode -> new ReasoningMeta.ModeMeta(mode.id(), mode.title()))
                .toList();
        int totalRequests = tasks.size() * methods.size() * modes.size()
                + tasks.size() * modes.size();
        return new ReasoningMeta(properties.model(), totalRequests, tasks, methods, modes);
    }

    @PostMapping("/experiments")
    public ResponseEntity<ReasoningJob> start() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.start());
    }

    @GetMapping("/experiments/{id}")
    public ReasoningJob find(@PathVariable String id) {
        return jobService.find(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Эксперимент не найден"
                ));
    }
}
