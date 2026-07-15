package de.civitascore.modelforge.validation;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import de.civitascore.modelforge.contract.Diagnostic;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in validation of an artifact document against the CORE JSON Schema it declares.
 *
 * <p>A document is validated only when its {@code $schema} names a known CORE artifact schema
 * (mapping, pipeline, datasource, datasink, dataset, datastructure); anything else — no
 * {@code $schema}, the plain JSON Schema meta-schema, or an unknown URI — is passed through
 * unvalidated. This is deliberately transitional: today host payloads that are not CORE-IR
 * conformant simply omit the {@code $schema} declaration and skip the check. The end state is
 * that every stored artifact declares and satisfies its CORE schema.
 */
public class CoreSchemaValidator {

    private static final Map<String, String> SCHEMA_RESOURCE = Map.of(
        "https://civitasconnect.digital/core-dataset/v1", "dataset.schema.json",
        "https://civitasconnect.digital/core-datastructure/v1", "datastructure.schema.json",
        "https://civitasconnect.digital/core/mapping/v1", "mapping.schema.json",
        "https://civitasconnect.digital/core/pipeline/v1", "pipeline.schema.json",
        "https://civitasconnect.digital/core/datasource/v1", "datasource.schema.json",
        "https://civitasconnect.digital/core/datasink/v1", "datasink.schema.json"
    );

    private final ObjectMapper mapper;
    private final JsonSchemaFactory factory = CoreJsonSchemaFactory.withCoreAnnotations();
    private final Map<String, JsonSchema> compiled = new ConcurrentHashMap<>();

    public CoreSchemaValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Validates {@code document} against the CORE schema named by its {@code $schema} field.
     *
     * @return empty when the document declares no known CORE {@code $schema} or is valid; one
     *     {@link Diagnostic} per violation otherwise
     */
    public List<Diagnostic> validate(JsonNode document) {
        if (document == null || !document.isObject()) {
            return List.of();
        }
        String declared = document.path("$schema").asText(null);
        String resource = declared == null ? null : SCHEMA_RESOURCE.get(declared);
        if (resource == null) {
            return List.of();
        }
        JsonSchema schema = compiled.computeIfAbsent(resource, this::load);
        return schema.validate(JacksonBridge.toJackson2(document)).stream()
            .map(e -> SchemaErrors.toDiagnostic(e, "artifact-schema-violation"))
            .toList();
    }

    private JsonSchema load(String resource) {
        try (InputStream is = getClass().getResourceAsStream("/" + resource)) {
            if (is == null) {
                throw new IllegalStateException("Missing CORE schema resource: " + resource);
            }
            return factory.getSchema(JacksonBridge.toJackson2(mapper.readTree(is)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not load CORE schema " + resource, e);
        }
    }
}
