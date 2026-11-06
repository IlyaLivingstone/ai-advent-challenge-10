package com.ilyalivingstone.aiadvent.day04;

import com.ilyalivingstone.aiadvent.day04.llm.DeepSeekProperties;
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
@RequestMapping("/api/temperature")
public class TemperatureController {

    private final TemperatureTaskCatalog catalog;
    private final TemperatureJobService jobService;
    private final DeepSeekProperties properties;

    public TemperatureController(
            TemperatureTaskCatalog catalog,
            TemperatureJobService jobService,
            DeepSeekProperties properties
    ) {
        this.catalog = catalog;
        this.jobService = jobService;
        this.properties = properties;
    }

    @GetMapping("/meta")
    public TemperatureMeta meta() {
        List<TemperatureMeta.TaskMeta> tasks = catalog.tasks().stream()
                .map(task -> new TemperatureMeta.TaskMeta(
                        task.id(), task.kind().id(), task.kind().title(), task.title(), task.prompt()
                ))
                .toList();
        List<TemperatureMeta.TemperatureMetaItem> temps = Arrays.stream(TemperatureLevel.values())
                .map(level -> new TemperatureMeta.TemperatureMetaItem(level.id(), level.label(), level.value()))
                .toList();
        int total = tasks.size() * temps.size() * TemperatureExperimentService.RUNS_PER_COMBINATION;
        return new TemperatureMeta(properties.model(), TemperatureExperimentService.RUNS_PER_COMBINATION, total, tasks, temps);
    }

    @PostMapping("/experiments")
    public ResponseEntity<TemperatureJob> start() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.start());
    }

    @GetMapping("/experiments/{id}")
    public TemperatureJob find(@PathVariable String id) {
        return jobService.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Эксперимент не найден"));
    }
}
