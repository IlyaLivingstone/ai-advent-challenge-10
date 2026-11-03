package com.ilyalivingstone.aiadvent.day02.api;

import com.ilyalivingstone.aiadvent.day02.experiment.ExperimentCatalog;
import com.ilyalivingstone.aiadvent.day02.experiment.ExperimentJob;
import com.ilyalivingstone.aiadvent.day02.experiment.ExperimentJobService;
import com.ilyalivingstone.aiadvent.day02.experiment.ExperimentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

    private final ExperimentJobService jobService;
    private final ExperimentCatalog catalog;

    public ExperimentController(ExperimentJobService jobService, ExperimentCatalog catalog) {
        this.jobService = jobService;
        this.catalog = catalog;
    }

    @GetMapping("/meta")
    public ExperimentMeta meta() {
        List<ExperimentMeta.VariantMeta> variants = catalog.variants().stream()
                .map(spec -> new ExperimentMeta.VariantMeta(
                        spec.variant().id(),
                        spec.variant().title(),
                        spec.maxTokens(),
                        spec.stopSequences(),
                        spec.formatInstruction(),
                        spec.jsonMode(),
                        spec.schemaInstruction()
                ))
                .toList();
        return new ExperimentMeta(
                catalog.defaultPrompt(),
                3,
                ExperimentService.MAX_REPEATS,
                ExperimentCatalog.MAX_PROMPT_LENGTH,
                variants
        );
    }

    @PostMapping("/preview")
    public ExperimentPreview preview(@Valid @RequestBody PreviewPromptRequest request) {
        String normalizedPrompt = catalog.normalizePrompt(request.prompt());
        List<ExperimentPreview.PreviewVariant> variants = catalog.variantsFor(normalizedPrompt).stream()
                .map(spec -> new ExperimentPreview.PreviewVariant(
                        spec.variant().id(),
                        spec.variant().title(),
                        spec.maxTokens(),
                        spec.stopSequences(),
                        spec.formatInstruction(),
                        spec.jsonMode(),
                        spec.schemaInstruction(),
                        spec.prompt()
                ))
                .toList();
        return new ExperimentPreview(normalizedPrompt, variants);
    }

    @PostMapping
    public ResponseEntity<ExperimentJob> start(@Valid @RequestBody StartExperimentRequest request) {
        ExperimentJob job = jobService.start(request.repeatCount(), request.prompt());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/{id}")
    public ExperimentJob find(@PathVariable String id) {
        return jobService.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Эксперимент не найден"));
    }
}
