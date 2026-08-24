package de.civitascore.modelforge.validation;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.ValidationMessage;
import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.DiagnosticSeverity;
import de.civitascore.modelforge.urn.UrnParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validates JSON documents against JSON Schema 2020-12.
 *
 * <p>Used in two modes:
 * <ul>
 *   <li>{@link #validateSchema} - check that a node is itself a valid JSON Schema
 *   <li>{@link #validateData} - validate arbitrary data against a provided schema
 * </ul>
 */
public class ModelValidator {

    private static final Logger log = LoggerFactory.getLogger(ModelValidator.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonSchemaFactory schemaRegistry = CoreJsonSchemaFactory.withCoreAnnotations();

    /**
     * Pins the validator's message locale. Instance-validation messages come from the library's
     * resource bundles, which resolve against the JVM default locale, while this class's own
     * diagnostics are hardcoded English — so without pinning, one response could mix languages
     * depending on the host's locale.
     */
    private final SchemaValidatorsConfig config =
        SchemaValidatorsConfig.builder().locale(Locale.ENGLISH).build();

    /**
     * Validate {@code data} against {@code schema}.
     *
     * @return empty list if valid; one {@link Diagnostic} per violation otherwise
     */
    public List<Diagnostic> validateData(JsonNode schema, JsonNode data) {
        try {
            // CORE-URN $ref values resolve against the registry, not against schema files;
            // neutralise them first (mirroring validateSchema) so the validator does not try
            // to dereference urn:core:... and fail every conforming instance with a
            // "validator-error". The dependency graph owns urn-ref existence, not validation.
            JsonSchema jsonSchema = schemaRegistry.getSchema(JacksonBridge.toJackson2(neutralizeCoreUrnRefs(schema)), config);
            List<ValidationMessage> errors = jsonSchema.validate(JacksonBridge.toJackson2(data)).stream().toList();
            return errors.stream()
                .map(e -> SchemaErrors.toDiagnostic(e, "validation"))
                .toList();
        } catch (StackOverflowError e) {
            // Circular $ref chains can exhaust the stack before the library guards
            // kick in; report them as a diagnostic instead of crashing the request.
            return List.of(new Diagnostic(DiagnosticSeverity.ERROR,
                "Schema contains a circular $ref that cannot be resolved (recursion depth exceeded)",
                "validation", null));
        } catch (Exception e) {
            // A thrown validator/schema fault is an internal error, not a data violation — tag it
            // with a distinct code so callers can tell "we could not run validation" from
            // "your data is invalid" (which carries code "validation"). The third-party fault
            // detail is logged server-side, never leaked to the client.
            log.warn("Schema validation could not be run", e);
            return List.of(new Diagnostic(DiagnosticSeverity.ERROR, "Schema could not be validated", "validator-error", null));
        }
    }

    /**
     * Check that {@code schema} is a parseable JSON Schema 2020-12 document.
     *
     * <p>CORE-URN {@code $ref} values are treated as opaque (they resolve against the
     * registry, not against schema files) - they are neutralised before compilation.
     * All remaining refs are resolved eagerly so dangling local pointers
     * ({@code #/$defs/Missing}) are reported instead of failing later at data-validation time.
     *
     * @return empty list if valid; a single error diagnostic if parsing fails
     */
    public List<Diagnostic> validateSchema(JsonNode schema) {
        // Check local pointers ourselves first. The library reports a dangling $ref only through a
        // thrown fault, which the catch below has to reduce to a generic message so no third-party
        // detail escapes — leaving the author with no idea which pointer is wrong. This check is
        // first-party, so it can name the pointer and its location safely.
        List<Diagnostic> dangling = danglingLocalRefs(schema);
        if (!dangling.isEmpty()) return dangling;
        try {
            schemaRegistry.getSchema(JacksonBridge.toJackson2(neutralizeCoreUrnRefs(schema)), config).initializeValidators();
            return List.of();
        } catch (StackOverflowError e) {
            return List.of(new Diagnostic(DiagnosticSeverity.ERROR,
                "Schema contains a circular $ref that cannot be resolved (recursion depth exceeded)",
                "schema-parse", null));
        } catch (Exception e) {
            // The underlying parser fault is logged server-side; the client only sees a
            // stable, generic message (no third-party exception detail).
            log.warn("Invalid JSON Schema submitted for parsing", e);
            return List.of(new Diagnostic(DiagnosticSeverity.ERROR, "Invalid JSON Schema", "schema-parse", null));
        }
    }

    /**
     * One diagnostic per local {@code $ref} whose JSON Pointer does not resolve within the document,
     * each naming the offending pointer and the path it sits at. A {@code $ref} of {@code "#"} is
     * the document root and always resolves; CORE-URN refs are not local and are out of scope here.
     */
    private static List<Diagnostic> danglingLocalRefs(JsonNode root) {
        List<Diagnostic> out = new ArrayList<>();
        collectDanglingRefs(root, root, "$", out);
        return out;
    }

    private static void collectDanglingRefs(
            JsonNode root, JsonNode node, String path, List<Diagnostic> out) {
        if (node == null) return;
        if (node.isArray()) {
            int i = 0;
            for (JsonNode child : node) {
                collectDanglingRefs(root, child, path + "[" + i++ + "]", out);
            }
            return;
        }
        if (!node.isObject()) return;
        node.properties().forEach(e -> {
            JsonNode value = e.getValue();
            if ("$ref".equals(e.getKey()) && value.isTextual() && value.asText().startsWith("#")) {
                String ref = value.asText();
                String pointer = ref.substring(1);
                if (!pointer.isEmpty() && root.at(pointer).isMissingNode()) {
                    out.add(new Diagnostic(DiagnosticSeverity.ERROR,
                        "Unresolved local $ref '" + ref + "'", "schema-parse", path));
                }
            } else {
                collectDanglingRefs(root, value, path + "." + e.getKey(), out);
            }
        });
    }

    /**
     * Deep-copy {@code node} with every CORE-URN {@code $ref} removed (sibling keywords
     * are kept). The validator must not try to fetch {@code urn:core:...} documents -
     * their existence is the dependency graph concern.
     */
    private static JsonNode neutralizeCoreUrnRefs(JsonNode node) {
        if (node == null || !(node.isObject() || node.isArray())) return node;
        if (node.isArray()) {
            ArrayNode arr = MAPPER.createArrayNode();
            node.forEach(child -> arr.add(neutralizeCoreUrnRefs(child)));
            return arr;
        }
        ObjectNode copy = MAPPER.createObjectNode();
        node.properties().forEach(e -> {
            boolean isUrnRef = "$ref".equals(e.getKey())
                && e.getValue().isTextual()
                && UrnParser.isUrn(e.getValue().asText());
            if (!isUrnRef) copy.set(e.getKey(), neutralizeCoreUrnRefs(e.getValue()));
        });
        return copy;
    }
}
