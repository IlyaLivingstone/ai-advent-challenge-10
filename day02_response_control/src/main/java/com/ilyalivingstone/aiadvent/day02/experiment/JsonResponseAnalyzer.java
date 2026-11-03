package com.ilyalivingstone.aiadvent.day02.experiment;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class JsonResponseAnalyzer {

    private final JsonMapper jsonMapper;
    private final Schema schema;

    public JsonResponseAnalyzer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        this.schema = registry.getSchema(SchemaLocation.of("classpath:schemas/app-analysis.schema.json"));
        this.schema.initializeValidators();
    }

    public AnalysisResult analyze(String content) {
        if (content == null || content.isBlank()) {
            return AnalysisResult.empty("Ответ пустой");
        }

        JsonNode directNode = tryParse(content);
        boolean directJson = directNode != null;
        boolean normalizedJson = directJson;
        String normalizedContent = content.trim();
        String parseError = null;

        if (!directJson) {
            normalizedContent = extractJsonObject(content);
            normalizedJson = tryParse(normalizedContent) != null;
            if (!normalizedJson) {
                parseError = "Ответ не является JSON даже после удаления текста вокруг объекта";
            }
        }

        if (!normalizedJson) {
            return new AnalysisResult(false, false, false, normalizedContent, parseError, List.of());
        }

        List<String> schemaErrors = validate(normalizedContent);
        return new AnalysisResult(
                directJson,
                true,
                schemaErrors.isEmpty(),
                normalizedContent,
                null,
                schemaErrors
        );
    }

    private JsonNode tryParse(String content) {
        try {
            return jsonMapper.readTree(content);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private List<String> validate(String json) {
        try {
            return schema.validate(json, InputFormat.JSON).stream()
                    .map(Object::toString)
                    .toList();
        } catch (RuntimeException error) {
            return List.of("Ошибка валидации: " + error.getMessage());
        }
    }

    static String extractJsonObject(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd >= 0) {
                trimmed = trimmed.substring(firstLineEnd + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
