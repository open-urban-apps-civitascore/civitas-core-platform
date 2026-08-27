package de.civitascore.modelforge.application;

import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.core.port.RemoteSchemaRepository;
import de.civitascore.modelforge.validation.ModelValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A public catalogue composes an entity from shared documents, so the properties a pipeline needs
 * are usually behind an {@code http} {@code $ref}. Those references are resolved once on the import
 * path; the stored document must carry none, must still contain the referenced properties, and must
 * be acceptable to the validator, which does not resolve remote references.
 */
class RemoteRefResolverTest {

    private static final String ENTITY   = "https://example.org/dataModel.Weather/WeatherObserved/schema.json";
    private static final String COMMONS  = "https://example.org/data-models/common-schema.json";
    private static final String WEATHER  = "https://example.org/dataModel.Weather/weather-schema.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final ModelValidator validator = new ModelValidator();

    /** Serves canned documents and records what was requested. */
    private static final class StubUpstream implements RemoteSchemaRepository {
        private final Map<String, String> documents = new LinkedHashMap<>();
        private final List<String> requested = new ArrayList<>();
        private final ObjectMapper mapper = new ObjectMapper();

        StubUpstream serve(String url, String json) {
            documents.put(url, json);
            return this;
        }

        @Override
        public JsonNode fetchJson(String url) {
            requested.add(url);
            String body = documents.get(url);
            if (body == null) throw new UpstreamException("no such document: " + url);
            return mapper.readTree(body);
        }
    }

    private RemoteRefResolver resolverFor(StubUpstream upstream) {
        return new RemoteRefResolver(upstream, mapper);
    }

    /** The shape a Smart Data Models entity actually has: shared schemas plus its own fields. */
    private String catalogueEntity() {
        return """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "https://example.org/WeatherObserved/schema.json",
              "title": "Weather Observed",
              "type": "object",
              "allOf": [
                { "$ref": "%s#/definitions/GSMA-Commons" },
                { "$ref": "%s#/definitions/Weather-Commons" },
                {
                  "properties": {
                    "dateObserved": { "type": "string", "format": "date-time" },
                    "precipitation": { "type": "number" }
                  }
                }
              ],
              "required": ["dateObserved"]
            }
            """.formatted(COMMONS, WEATHER);
    }

    private StubUpstream catalogue() {
        return new StubUpstream()
            .serve(COMMONS, """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "$id": "https://example.org/common-schema.json",
                  "definitions": {
                    "GSMA-Commons": {
                      "properties": {
                        "id": { "type": "string" },
                        "name": { "type": "string" }
                      }
                    }
                  }
                }
                """)
            .serve(WEATHER, """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "$id": "https://example.org/weather-schema.json",
                  "definitions": {
                    "Weather-Commons": {
                      "properties": {
                        "temperature": { "type": "number" },
                        "relativeHumidity": { "type": "number" },
                        "windSpeed": { "type": "number" }
                      }
                    }
                  }
                }
                """);
    }

    private static List<String> remoteRefsOf(JsonNode node) {
        List<String> found = new ArrayList<>();
        collectRemoteRefs(node, found);
        return found;
    }

    private static void collectRemoteRefs(JsonNode node, List<String> found) {
        if (node == null) return;
        if (node.isArray()) {
            node.forEach(child -> collectRemoteRefs(child, found));
            return;
        }
        if (!node.isObject()) return;
        node.properties().forEach(e -> {
            if ("$ref".equals(e.getKey()) && e.getValue().isTextual()
                    && e.getValue().asText().startsWith("http")) {
                found.add(e.getValue().asText());
            } else {
                collectRemoteRefs(e.getValue(), found);
            }
        });
    }

    private static List<String> propertyNamesOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        collectPropertyNames(node, names);
        return names;
    }

    private static void collectPropertyNames(JsonNode node, List<String> names) {
        if (node == null || !(node.isObject() || node.isArray())) return;
        if (node.isArray()) {
            node.forEach(child -> collectPropertyNames(child, names));
            return;
        }
        node.properties().forEach(e -> {
            if ("properties".equals(e.getKey()) && e.getValue().isObject()) {
                e.getValue().propertyNames().forEach(names::add);
            }
            collectPropertyNames(e.getValue(), names);
        });
    }

    @Test
    void referencedPropertiesArePresentAndNoRemoteRefRemains() {
        JsonNode resolved = resolverFor(catalogue())
            .inlineRemoteRefs(mapper.readTree(catalogueEntity()));

        // The point of resolving: the fields the entity is imported for live in the shared documents.
        assertThat(propertyNamesOf(resolved))
            .contains("temperature", "relativeHumidity", "windSpeed", "id", "name")
            .contains("dateObserved", "precipitation");
        assertThat(remoteRefsOf(resolved)).isEmpty();
    }

    @Test
    void theResolvedDocumentIsAcceptedByTheValidator() {
        JsonNode asAuthored = mapper.readTree(catalogueEntity());
        // Precondition: the document is rejected as authored, because the validator does not — and
        // must not — dereference an http $ref.
        assertThat(validator.validateSchema(asAuthored))
            .extracting(Diagnostic::message)
            .containsExactly("Invalid JSON Schema");

        JsonNode resolved = resolverFor(catalogue()).inlineRemoteRefs(asAuthored);

        assertThat(validator.validateSchema(resolved)).isEmpty();
    }

    @Test
    void eachUpstreamDocumentIsFetchedOnlyOnce() {
        String entity = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "allOf": [
                { "$ref": "%s#/definitions/GSMA-Commons" },
                { "$ref": "%s#/definitions/GSMA-Commons" }
              ]
            }
            """.formatted(COMMONS, COMMONS);
        StubUpstream upstream = catalogue();

        resolverFor(upstream).inlineRemoteRefs(mapper.readTree(entity));

        assertThat(upstream.requested).containsExactly(COMMONS);
    }

    @Test
    void aPointerInsideAFetchedDocumentIsResolvedRatherThanLeftDangling() {
        // Weather-Commons refers to a sibling definition of its own document. Inlining only the
        // fragment would leave '#/definitions/Measure' pointing into the importing document, where
        // it does not exist.
        StubUpstream upstream = new StubUpstream().serve(WEATHER, """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "definitions": {
                "Weather-Commons": {
                  "properties": { "temperature": { "$ref": "#/definitions/Measure" } }
                },
                "Measure": { "type": "number", "minimum": -100 }
              }
            }
            """);
        String entity = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "allOf": [ { "$ref": "%s#/definitions/Weather-Commons" } ]
            }
            """.formatted(WEATHER);

        JsonNode resolved = resolverFor(upstream).inlineRemoteRefs(mapper.readTree(entity));

        assertThat(resolved.toString()).doesNotContain("$ref");
        assertThat(resolved.at("/allOf/0/properties/temperature/minimum").asInt()).isEqualTo(-100);
        assertThat(validator.validateSchema(resolved)).isEmpty();
    }

    @Test
    void aLocalPointerOfTheImportedDocumentIsPreserved() {
        String entity = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$defs": { "Celsius": { "type": "number" } },
              "properties": { "temperature": { "$ref": "#/$defs/Celsius" } }
            }
            """;

        JsonNode resolved = resolverFor(new StubUpstream()).inlineRemoteRefs(mapper.readTree(entity));

        assertThat(resolved.at("/properties/temperature/$ref").asText()).isEqualTo("#/$defs/Celsius");
        assertThat(validator.validateSchema(resolved)).isEmpty();
    }

    @Test
    void aCoreUrnReferenceIsLeftUntouchedAndNothingIsFetched() {
        String urn = "urn:core:platform:civitas:element:sta:Location:l2p8y4efpu:1.0.0";
        String entity = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "properties": { "location": { "$ref": "%s" } }
            }
            """.formatted(urn);
        StubUpstream upstream = new StubUpstream();

        JsonNode resolved = resolverFor(upstream).inlineRemoteRefs(mapper.readTree(entity));

        assertThat(resolved.at("/properties/location/$ref").asText()).isEqualTo(urn);
        assertThat(upstream.requested).isEmpty();
    }

    @Test
    void keywordsAlongsideAReferenceSurvive() {
        String entity = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "allOf": [
                { "$ref": "%s#/definitions/GSMA-Commons", "description": "shared identity" }
              ]
            }
            """.formatted(COMMONS);

        JsonNode resolved = resolverFor(catalogue()).inlineRemoteRefs(mapper.readTree(entity));

        assertThat(resolved.toString()).contains("shared identity");
        assertThat(propertyNamesOf(resolved)).contains("id", "name");
        assertThat(remoteRefsOf(resolved)).isEmpty();
    }

    @Test
    void theIdentityOfAFetchedDocumentIsNotInlined() {
        JsonNode resolved = resolverFor(catalogue())
            .inlineRemoteRefs(mapper.readTree(catalogueEntity()));

        // The importing document keeps its own $id; the upstream documents' identities must not
        // appear as embedded resources, which would assert a foreign identity for a subschema.
        assertThat(resolved.at("/$id").asText()).isEqualTo("https://example.org/WeatherObserved/schema.json");
        assertThat(resolved.at("/allOf/0/$id").isMissingNode()).isTrue();
        assertThat(resolved.toString()).doesNotContain("common-schema.json");
    }

    @Test
    void aReferenceToAWholeDocumentInlinesItWithoutItsIdentity() {
        StubUpstream upstream = new StubUpstream().serve(WEATHER, """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "https://example.org/weather-schema.json",
              "type": "object",
              "properties": { "temperature": { "type": "number" } }
            }
            """);
        String entity = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "https://example.org/entity.json",
              "allOf": [ { "$ref": "%s" } ]
            }
            """.formatted(WEATHER);

        JsonNode resolved = resolverFor(upstream).inlineRemoteRefs(mapper.readTree(entity));

        assertThat(propertyNamesOf(resolved)).contains("temperature");
        assertThat(remoteRefsOf(resolved)).isEmpty();
        assertThat(resolved.at("/allOf/0/$id").isMissingNode()).isTrue();
        assertThat(resolved.at("/allOf/0/$schema").isMissingNode()).isTrue();
        assertThat(resolved.at("/$id").asText()).isEqualTo("https://example.org/entity.json");
        assertThat(validator.validateSchema(resolved)).isEmpty();
    }

    @Test
    void aCircularRemoteReferenceIsRefusedRatherThanFollowedForever() {
        StubUpstream upstream = new StubUpstream()
            .serve(COMMONS, """
                { "definitions": { "A": { "allOf": [ { "$ref": "%s#/definitions/B" } ] } } }
                """.formatted(WEATHER))
            .serve(WEATHER, """
                { "definitions": { "B": { "allOf": [ { "$ref": "%s#/definitions/A" } ] } } }
                """.formatted(COMMONS));
        String entity = """
            { "allOf": [ { "$ref": "%s#/definitions/A" } ] }
            """.formatted(COMMONS);

        assertThatThrownBy(() -> resolverFor(upstream).inlineRemoteRefs(mapper.readTree(entity)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Circular remote $ref");
    }

    @Test
    void aReferenceThatDoesNotResolveIsReportedAsSuch() {
        String entity = """
            { "allOf": [ { "$ref": "%s#/definitions/Missing" } ] }
            """.formatted(COMMONS);

        assertThatThrownBy(() -> resolverFor(catalogue()).inlineRemoteRefs(mapper.readTree(entity)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not resolve");
    }

    @Test
    void anImportIsCappedInHowManyUpstreamDocumentsItMayFetch() {
        StubUpstream upstream = new StubUpstream();
        StringBuilder refs = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            String url = "https://example.org/doc" + i + ".json";
            upstream.serve(url, "{ \"definitions\": { \"D\": { \"type\": \"object\" } } }");
            if (i > 0) refs.append(',');
            refs.append("{ \"$ref\": \"").append(url).append("#/definitions/D\" }");
        }
        JsonNode entity = mapper.readTree("{ \"allOf\": [" + refs + "] }");

        assertThatThrownBy(() -> resolverFor(upstream).inlineRemoteRefs(entity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("more than 10 upstream documents");
    }
}
