package de.civitascore.modelforge.validation;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import de.civitascore.modelforge.contract.ArtifactKind;
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

    /**
     * The CORE schema every artifact kind is validated against on write. {@code ELEMENT} is absent:
     * an Element is an arbitrary JSON-Schema/XSD document, validated as a valid schema on import
     * ({@code ModelValidator}) rather than against a fixed CORE artifact schema.
     */
    private static final Map<ArtifactKind, String> RESOURCE_FOR_KIND = Map.of(
        ArtifactKind.MAPPING, "mapping.schema.json",
        ArtifactKind.PIPELINE, "pipeline.schema.json",
        ArtifactKind.DATA_SOURCE, "datasource.schema.json",
        ArtifactKind.DATA_SINK, "datasink.schema.json",
        ArtifactKind.DATA_STRUCTURE, "datastructure.schema.json",
        ArtifactKind.DATA_SET, "dataset.schema.json"
    );

    /**
     * The {@code $schema} URI stamped onto a payload document on write, so it self-describes and
     * satisfies its schema's {@code required:["$schema"]}. The opaque payload kinds plus the DataSet
     * manifest are here — the DataSet manifest is a CORE document (not a JSON Schema) and carries the
     * {@code core-dataset/v1} {@code $schema} its schema mandates as a {@code const}. Only DataStructure
     * carries the JSON-Schema meta-schema {@code $schema} (it IS a JSON Schema) and is validated by
     * kind without overwriting it; Element is never stamped.
     */
    private static final Map<ArtifactKind, String> SCHEMA_URI_FOR_KIND = Map.of(
        ArtifactKind.MAPPING, "https://civitasconnect.digital/core/mapping/v1",
        ArtifactKind.PIPELINE, "https://civitasconnect.digital/core/pipeline/v1",
        ArtifactKind.DATA_SOURCE, "https://civitasconnect.digital/core/datasource/v1",
        ArtifactKind.DATA_SINK, "https://civitasconnect.digital/core/datasink/v1",
        ArtifactKind.DATA_SET, "https://civitasconnect.digital/core-dataset/v1"
    );

    /** The {@code $schema} URI to stamp for {@code kind}, or {@code null} if it must not be stamped. */
    public static String schemaUriToStamp(ArtifactKind kind) {
        return kind == null ? null : SCHEMA_URI_FOR_KIND.get(kind);
    }

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
        return validateAgainst(resource, document);
    }

    /**
     * Validates {@code document} against the CORE schema for its artifact {@code kind}, regardless of
     * the {@code $schema} the document declares (a DataStructure declares the JSON-Schema meta-schema,
     * yet must satisfy {@code datastructure.schema.json}). Kinds without a fixed CORE schema
     * ({@code ELEMENT}) are validated elsewhere and pass through here.
     *
     * @return one {@link Diagnostic} per violation, empty when valid or when the kind has no CORE schema
     */
    public List<Diagnostic> validate(ArtifactKind kind, JsonNode document) {
        if (document == null || !document.isObject() || kind == null) {
            return List.of();
        }
        String resource = RESOURCE_FOR_KIND.get(kind);
        if (resource == null) {
            return List.of();
        }
        return validateAgainst(resource, document);
    }

    private List<Diagnostic> validateAgainst(String resource, JsonNode document) {
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
