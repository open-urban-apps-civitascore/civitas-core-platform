package de.civitascore.modelforge.validation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CoreSchemaExtensionsTest {

    private static final List<String> CORE_SCHEMA_RESOURCES = List.of(
        "dataset.schema.json",
        "datasource.schema.json",
        "datasink.schema.json",
        "mapping.schema.json",
        "pipeline.schema.json",
        "datastructure.schema.json",
        "artifact-envelope.schema.json",
        "core-schema-extensions.schema.json"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extensionSchema_acceptsKnownExtensionShapes() throws Exception {
        JsonSchema schema = extensionSchema();

        assertThat(schema.validate(JacksonBridge.toJackson2(wrapper("x-core-ref", """
            { "type": "urn:core:type:Element" }
            """)))).isEmpty();
        assertThat(schema.validate(JacksonBridge.toJackson2(wrapper("x-core-ref", """
            { "type": "urn:core:platform:civitas:element:common:Strasse:u8pwgr2zzg:1.0.0" }
            """)))).isEmpty();
        assertThat(schema.validate(JacksonBridge.toJackson2(
                wrapper("x-xsd-source", "\"urn:core:platform:civitas:element:common:AirQuality:wyonmizavr:1.0.0\""))))
            .isEmpty();
        assertThat(schema.validate(JacksonBridge.toJackson2(wrapper("x-ui-position", "{ \"x\": 120, \"y\": 80 }"))))
            .isEmpty();
    }

    @Test
    void extensionSchema_rejectsUnknownOrInconsistentExtensions() throws Exception {
        JsonSchema schema = extensionSchema();

        assertThat(schema.validate(JacksonBridge.toJackson2(wrapper("x-unknown", "true")))).isNotEmpty();
        assertThat(schema.validate(JacksonBridge.toJackson2(wrapper("x-core-ref", """
            { "type": "#/$defs/Mapping", "collection": "mappings" }
            """)))).isNotEmpty();
        assertThat(schema.validate(JacksonBridge.toJackson2(wrapper("x-core-ref", """
            { "type": "urn:core:type:Mapping", "scope": "global", "group": "mappings" }
            """)))).isNotEmpty();
        assertThat(schema.validate(JacksonBridge.toJackson2(wrapper("x-core-ref", """
            { "type": "#/$defs/Element", "key": "$id" }
            """)))).isNotEmpty();
        assertThat(schema.validate(JacksonBridge.toJackson2(wrapper("x-core-ref", """
            { "type": "urn:core:type:Element", "scope": "registry" }
            """)))).isNotEmpty();
        assertThat(schema.validate(JacksonBridge.toJackson2(wrapper("x-core-ref", """
            { "type": "#/$defs/Element", "scope": "dataset" }
            """)))).isNotEmpty();
        assertThat(schema.validate(JacksonBridge.toJackson2(wrapper("x-core-ref", """
            { "type": "Element" }
            """)))).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "dataset.schema.json",
        "datasource.schema.json",
        "datasink.schema.json",
        "mapping.schema.json",
        "pipeline.schema.json",
        "datastructure.schema.json",
        "artifact-envelope.schema.json",
        "core-schema-extensions.schema.json"
    })
    void coreSchemas_useOnlyDeclaredXExtensions(String resource) throws Exception {
        JsonSchema extensionSchema = extensionSchema();
        List<String> violations = new ArrayList<>();

        collectXExtensionViolations(load(resource), resource, false, extensionSchema, violations);

        assertThat(violations).isEmpty();
    }

    @Test
    void pipelineUiPosition_matchesDeclaredExtensionShape() throws Exception {
        JsonNode extensions = load("core-schema-extensions.schema.json");
        JsonNode pipeline = load("pipeline.schema.json");

        assertThat(pipeline.at("/$defs/Position"))
            .isEqualTo(extensions.at("/$defs/UiPosition"));
    }

    @Test
    void allCoreSchemaResources_areCoveredByExtensionGovernanceTest() throws Exception {
        List<String> actualSchemaResources;
        try (var files = Files.list(Path.of("src/main/resources"))) {
            actualSchemaResources = files
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".schema.json"))
                .sorted()
                .toList();
        }

        assertThat(CORE_SCHEMA_RESOURCES)
            .containsExactlyInAnyOrderElementsOf(actualSchemaResources);
    }

    private ObjectNode wrapper(String extensionName, String rawJsonValue) throws Exception {
        ObjectNode wrapper = mapper.createObjectNode();
        wrapper.set(extensionName, mapper.readTree(rawJsonValue));
        return wrapper;
    }

    private void collectXExtensionViolations(JsonNode node,
                                             String path,
                                             boolean parentIsProperties,
                                             JsonSchema extensionSchema,
                                             List<String> violations) {
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectXExtensionViolations(node.get(i), path + "/" + i, false, extensionSchema, violations);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            JsonNode value = field.getValue();
            String childPath = path + "/" + name;

            if (name.startsWith("x-")) {
                if (parentIsProperties) {
                    assertKnownInstanceExtensionProperty(name, childPath, violations);
                } else {
                    assertKnownSchemaExtensionKeyword(name, value, childPath, extensionSchema, violations);
                }
            }

            collectXExtensionViolations(value, childPath, "properties".equals(name), extensionSchema, violations);
        }
    }

    private void assertKnownInstanceExtensionProperty(String name, String path, List<String> violations) {
        JsonNode declaredProperties = loadUnchecked("core-schema-extensions.schema.json").path("properties");
        if (!declaredProperties.has(name)) {
            violations.add(path + " is not declared in core-schema-extensions.schema.json");
        }
    }

    private void assertKnownSchemaExtensionKeyword(String name,
                                                   JsonNode value,
                                                   String path,
                                                   JsonSchema extensionSchema,
                                                   List<String> violations) {
        ObjectNode wrapper = mapper.createObjectNode();
        wrapper.set(name, value.deepCopy());
        Set<ValidationMessage> messages = extensionSchema.validate(JacksonBridge.toJackson2(wrapper));
        if (!messages.isEmpty()) {
            violations.add(path + " does not match core-schema-extensions.schema.json: " + messages);
        }
    }

    private JsonSchema extensionSchema() throws Exception {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(JacksonBridge.toJackson2(load("core-schema-extensions.schema.json")));
    }

    private JsonNode load(String resource) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/" + resource)) {
            assertThat(is).as("resource " + resource).isNotNull();
            return mapper.readTree(is);
        }
    }

    private JsonNode loadUnchecked(String resource) {
        try {
            return load(resource);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load resource " + resource, e);
        }
    }
}
