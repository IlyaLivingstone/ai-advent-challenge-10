package com.ilyalivingstone.aiadvent.day05;

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
@RequestMapping("/api/models")
public class ModelController {

    private final ModelCatalog catalog;
    private final ModelJobService jobService;
    private final ReasoningTaskCatalog taskCatalog;

    public ModelController(
            ModelCatalog catalog,
            ModelJobService jobService,
            ReasoningTaskCatalog taskCatalog
    ) {
        this.catalog = catalog;
        this.jobService = jobService;
        this.taskCatalog = taskCatalog;
    }

    @GetMapping("/meta")
    public ModelMeta meta() {
        List<ModelMeta.TaskMeta> taskMeta = taskCatalog.tasks().stream()
                .map(task -> new ModelMeta.TaskMeta(task.id(), task.title(), task.category()))
                .toList();
        List<ModelMeta.ModelMetaItem> modelMeta = catalog.models().stream()
                .map(model -> new ModelMeta.ModelMetaItem(
                        model.id(), model.title(), model.role(), model.model(),
                        model.timeoutSeconds(), model.inputPricePerMillion(), model.outputPricePerMillion()
                ))
                .toList();
        return new ModelMeta(taskMeta.size(), taskMeta.size() * modelMeta.size(), taskMeta, modelMeta);
    }

    @PostMapping("/experiments")
    public ResponseEntity<ModelJob> start() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.start());
    }

    @GetMapping("/experiments/{id}")
    public ModelJob find(@PathVariable String id) {
        return jobService.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Эксперимент не найден"));
    }
}
