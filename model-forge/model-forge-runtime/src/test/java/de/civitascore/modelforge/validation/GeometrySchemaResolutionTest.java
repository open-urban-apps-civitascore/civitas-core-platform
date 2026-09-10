package de.civitascore.modelforge.validation;

import de.civitascore.modelforge.contract.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A geometry attribute is authored as a reference to its published GeoJSON schema, and both the
 * PostGIS and GeoServer adapters read that URL to derive a geometry column and a native CRS — so the
 * reference has to survive in the stored document and still be resolvable while validating.
 *
 * <p>It resolves from a vendored copy on the classpath. Nothing is fetched, and only the exact
 * published IRIs resolve: every other absolute reference stays refused.
 */
class GeometrySchemaResolutionTest {

    private static final String[] GEOMETRY_TYPES = {
        "Point", "LineString", "Polygon", "MultiPoint", "MultiLineString", "MultiPolygon",
        "GeometryCollection",
    };

    private final ObjectMapper mapper = new ObjectMapper();
    private final ModelValidator validator = new ModelValidator();

    private JsonNode model(String properties) {
        return mapper.readTree("""
            { "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "urn:core:global:core:datastructure:mobility:sensor:abcd1234",
              "type": "object",
              "properties": %s }
            """.formatted(properties));
    }

    /** A single property holding {@code $ref}, as the model editor exports a geometry attribute. */
    private JsonNode geometryModel(String type) {
        return model("""
            { "location": { "$ref": "https://geojson.org/schema/%s.json", "crs": "EPSG:25832" } }
            """.formatted(type));
    }

    @Test
    void aGeometryModelIsAValidSchema() {
        assertThat(validator.validateSchema(geometryModel("Point"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Point", "LineString", "Polygon", "MultiPoint", "MultiLineString",
                            "MultiPolygon", "GeometryCollection"})
    void everyGeometryTypeResolves(String type) {
        assertThat(validator.validateSchema(geometryModel(type))).isEmpty();
    }

    /**
     * A multivalued geometry attribute is exported as an array, so the reference is not always at
     * property level.
     */
    @Test
    void aGeometryReferenceNestedInItemsResolves() {
        JsonNode schema = model("""
            { "track": { "type": "array",
                         "items": { "$ref": "https://geojson.org/schema/LineString.json" } } }
            """);

        assertThat(validator.validateSchema(schema)).isEmpty();
    }

    @Test
    void aValidGeometryValueIsAccepted() {
        JsonNode data = mapper.readTree("""
            { "location": { "type": "Point", "coordinates": [8.4, 49.0] } }
            """);

        assertThat(validator.validateData(geometryModel("Point"), data)).isEmpty();
    }

    @Test
    void aGeometryValueMissingItsCoordinatesIsRejectedNamingTheProperty() {
        JsonNode data = mapper.readTree("{\"location\":{\"type\":\"Point\"}}");

        List<Diagnostic> diagnostics = validator.validateData(geometryModel("Point"), data);

        assertThat(diagnostics).isNotEmpty();
        assertThat(diagnostics).anySatisfy(d ->
            assertThat(d.message()).contains("location").contains("coordinates"));
    }

    @Test
    void aGeometryOfTheWrongTypeIsRejected() {
        JsonNode data = mapper.readTree("""
            { "location": { "type": "Polygon",
                            "coordinates": [[[8.0,49.0],[8.1,49.1],[8.2,49.0],[8.0,49.0]]] } }
            """);

        assertThat(validator.validateData(geometryModel("Point"), data)).isNotEmpty();
    }

    /**
     * The vendored documents declare draft-07. Resolving one must not re-dialect the referencing
     * document, which is 2020-12 — where {@code exclusiveMinimum} is a number rather than the
     * boolean of the older drafts.
     */
    @Test
    void aVendoredDraft07FileDoesNotChangeTheReferencingDocumentsDialect() {
        JsonNode schema = model("""
            { "location": { "$ref": "https://geojson.org/schema/Point.json" },
              "count": { "type": "integer", "exclusiveMinimum": 5 } }
            """);

        assertThat(validator.validateSchema(schema)).isEmpty();
        assertThat(validator.validateData(schema, mapper.readTree("{\"count\":5}"))).isNotEmpty();
        assertThat(validator.validateData(schema, mapper.readTree("{\"count\":6}"))).isEmpty();
    }

    /**
     * Only the seven published geometry IRIs resolve. Everything else — another host, another
     * document in the same catalogue, a {@code classpath:} or {@code file:} scheme, or a relative
     * segment trying to walk out of the vendored directory — stays refused, so serving these seven
     * from the classpath does not become a way to read arbitrary classpath resources. Their content
     * would otherwise come back inside validation messages.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "https://evil.example/schema.json",
        "https://geojson.org/schema/Feature.json",
        "http://geojson.org/schema/Point.json",
        "https://geojson.org/schema/../application.json",
        "https://geojson.org/schema/%2E%2E/application.json",
        "classpath:datastructure.schema.json",
        "classpath:geojson/../datastructure.schema.json",
        "file:///etc/passwd",
    })
    void anyOtherAbsoluteReferenceIsStillRefused(String ref) {
        JsonNode schema = model("{ \"x\": { \"$ref\": \"" + ref + "\" } }");

        assertThat(validator.validateSchema(schema))
            .isNotEmpty()
            .allSatisfy(d -> assertThat(d.code()).isEqualTo("schema-parse"));
    }

    /**
     * Guards a refresh of the vendored copies, and any document added to the loader later: a
     * vendored document must reference no other document, because there is nothing to resolve such
     * a reference out of a vendored copy with. Iterates the loader's own registry so a new entry is
     * covered without touching this test.
     */
    @Test
    void everyVendoredDocumentIsPresentAndSelfContained() {
        assertThat(VendoredSchemaLoader.vendoredIris()).isNotEmpty();

        for (String iri : VendoredSchemaLoader.vendoredIris()) {
            String resource = VendoredSchemaLoader.resourceFor(iri);
            JsonNode document;
            try (InputStream in = getClass().getResourceAsStream("/" + resource)) {
                assertThat(in).as("vendored resource %s", resource).isNotNull();
                document = mapper.readTree(in);
            } catch (IOException e) {
                throw new AssertionError(resource, e);
            }

            assertThat(externalRefs(document))
                .as("%s must not reference another document", resource)
                .isEmpty();
        }
    }

    /** Every geometry type the model editor can export has a vendored copy. */
    @Test
    void everyGeometryTypeTheEditorExportsIsVendored() {
        for (String type : GEOMETRY_TYPES) {
            assertThat(VendoredSchemaLoader.resourceFor("https://geojson.org/schema/" + type + ".json"))
                .as("vendored copy for %s", type)
                .isNotNull();
        }
    }

    private static List<String> externalRefs(JsonNode node) {
        List<String> found = new ArrayList<>();
        collectExternalRefs(node, found);
        return found;
    }

    private static void collectExternalRefs(JsonNode node, List<String> found) {
        if (node.isArray()) {
            node.forEach(child -> collectExternalRefs(child, found));
            return;
        }
        if (!node.isObject()) return;
        node.properties().forEach(e -> {
            if ("$ref".equals(e.getKey()) && e.getValue().isTextual()
                && !e.getValue().asString().startsWith("#")) {
                found.add(e.getValue().asString());
            }
            collectExternalRefs(e.getValue(), found);
        });
    }
}
