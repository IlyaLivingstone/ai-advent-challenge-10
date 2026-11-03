package com.ilyalivingstone.aiadvent.day02.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ExperimentCatalog {

    public static final int MAX_PROMPT_LENGTH = 4000;

    public static final String DEFAULT_PROMPT = """
            Проанализируй идею мобильного приложения для учёта личных финансов.
            Верни название, целевую аудиторию, три ключевые функции и основной риск.
            """.strip();

    private static final String FORMAT_INSTRUCTION = """
            Ответь только валидным JSON-объектом без Markdown, пояснений и текста до или после JSON.
            Схема ответа:
            {
              "name": "название приложения, строка",
              "audience": ["1–3 сегмента целевой аудитории"],
              "features": ["ровно 3 ключевые функции"],
              "risk": "основной риск, строка"
            }
            """.strip();

    private final String schema;

    public ExperimentCatalog() {
        this.schema = loadSchema();
    }

    public List<VariantSpec> variants() {
        return variantsFor(DEFAULT_PROMPT);
    }

    public List<VariantSpec> variantsFor(String basePrompt) {
        String prompt = normalizePrompt(basePrompt);
        String formattedPrompt = prompt + "\n\n" + FORMAT_INSTRUCTION;
        String schemaPrompt = formattedPrompt + "\n\nJSON Schema:\n" + schema;
        return List.of(
                new VariantSpec(ExperimentVariant.BASELINE, prompt, 1500, List.of(), false, false, false),
                new VariantSpec(ExperimentVariant.EXPLICIT_FORMAT, formattedPrompt, 1500, List.of(), true, false, false),
                new VariantSpec(ExperimentVariant.MAX_TOKENS, formattedPrompt, 300, List.of(), true, false, false),
                new VariantSpec(ExperimentVariant.STOP_SEQUENCE, formattedPrompt, 1500, List.of("\n\n"), true, false, false),
                new VariantSpec(ExperimentVariant.STRICT_SCHEMA, schemaPrompt, 1500, List.of(), true, true, true)
        );
    }

    public String defaultPrompt() {
        return DEFAULT_PROMPT;
    }

    public String normalizePrompt(String basePrompt) {
        if (basePrompt == null || basePrompt.isBlank()) {
            return DEFAULT_PROMPT;
        }
        String prompt = basePrompt.strip();
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            throw new IllegalArgumentException(
                    "Запрос не должен превышать " + MAX_PROMPT_LENGTH + " символов"
            );
        }
        return prompt;
    }

    public String schema() {
        return schema;
    }

    private String loadSchema() {
        try {
            return new ClassPathResource("schemas/app-analysis.schema.json")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Не удалось загрузить JSON Schema", error);
        }
    }
}
