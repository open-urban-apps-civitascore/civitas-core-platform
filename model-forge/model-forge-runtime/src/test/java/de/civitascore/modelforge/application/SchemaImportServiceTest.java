package de.civitascore.modelforge.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.core.port.RemoteSchemaRepository;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.urn.UrnService;
import de.civitascore.modelforge.validation.CoreSchemaValidator;
import de.civitascore.modelforge.validation.ModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SchemaImportService#importSchema} focused on how a JSON Schema with
 * multiple {@code $defs} entities is split into separate, globally addressable Elements.
 *
 * <p>Each embedded definition becomes its own model_forge.artifact with its own CORE URN, internal
 * {@code #/$defs/Name} references are rewritten to those URNs, and the redundant {@code $defs}
 * block is dropped — so the extracted entities reference each other globally and form
 * dependency-graph edges.
 *
 * <p>Name-derived URNs carry a minted disambiguator in its own segment
 * ({@code …:<name>:<disambiguator>:<version>}) so equal titles/def keys can never collide onto
 * the same logical URN; explicitly supplied CORE-URN {@code $id}s are kept verbatim. Assertions
 * therefore match URNs by their (clean) name segment.
 */
class SchemaImportServiceTest {


    private final ObjectMapper mapper = new ObjectMapper();
    private ArtifactRegistry registry;
    private RemoteSchemaRepository remoteFetcher;
    private DependencyGraphService graph;
    private SchemaImportService svc;

    @BeforeEach
    void setUp() {
        registry = mock(ArtifactRegistry.class);
        when(registry.resolveReference(anyString())).thenReturn(Optional.empty());
        // A mock does not run the interface's default methods, so the transaction seam has to be
        // stubbed to actually execute the work — otherwise the whole import silently stores nothing.
        when(registry.inTransaction(any())).thenAnswer(call -> call.getArgument(0, Supplier.class).get());
        ModelValidator validator = mock(ModelValidator.class);
        when(validator.validateSchema(any())).thenReturn(List.of());
        UrnService urns = new UrnService("platform", "civitas", "common", "1.0.0");
        SchemaRefExtractor refExtractor = new SchemaRefExtractor();
        graph = new DependencyGraphService(registry);
        ReferenceExistenceValidator refExistence = new ReferenceExistenceValidator(registry, refExtractor);
        remoteFetcher = mock(RemoteSchemaRepository.class);
        svc = new SchemaImportService(validator, mapper, registry,
                urns, refExtractor, graph, refExistence, remoteFetcher,
                new CoreSchemaValidator(mapper));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Import the schema and return every stored Element keyed by its $id (URN). */
    private Map<String, JsonNode> importAndCapture(JsonNode schema) {
        svc.importSchema(new SchemaImportRequest(schema));
        return capturedElements();
    }

    /** Every Element stored so far, keyed by its $id (URN). */
    private Map<String, JsonNode> capturedElements() {
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(registry, atLeastOnce()).storeElement(name.capture(), body.capture(), anySet(), anySet(), nullable(String.class), any(VersionBump.class));
        Map<String, JsonNode> stored = new LinkedHashMap<>();
        List<String> names = name.getAllValues();
        List<JsonNode> bodies = body.getAllValues();
        for (int i = 0; i < names.size(); i++) {
            stored.put(bodies.get(i).path("$id").asText(names.get(i)), bodies.get(i));
        }
        return stored;
    }

    /** The unique stored URN whose name segment equals the given base (e.g. "DefProduct"). */
    private static String urnOf(Map<String, JsonNode> stored, String base) {
        List<String> hits = stored.keySet().stream()
            .filter(urn -> base.equals(UrnParser.nameFromUrn(urn)))
            .toList();
        assertThat(hits).as("exactly one minted URN with base '%s' in %s", base, stored.keySet()).hasSize(1);
        return hits.getFirst();
    }

    /** The stored Element whose name segment equals the given base. */
    private static JsonNode elementOf(Map<String, JsonNode> stored, String base) {
        return stored.get(urnOf(stored, base));
    }

    private JsonNode catalog() {
        try {
            return mapper.readTree("""
                {
                  "$schema":"https://json-schema.org/draft/2020-12/schema",
                  "title":"DefCatalog","type":"object",
                  "properties":{"top":{"$ref":"#/$defs/DefProduct"}},
                  "$defs":{
                    "DefProduct":{"type":"object","properties":{"name":{"type":"string"},"cat":{"$ref":"#/$defs/DefCategory"}}},
                    "DefCategory":{"type":"object","properties":{"label":{"type":"string"}}},
                    "DefSupplier":{"$id":"urn:core:platform:civitas:element:common:CustomSupplier:w6c7eam3x0:1.0.0","type":"object","properties":{"cat":{"$ref":"#/$defs/DefCategory"}}}
                  }
                }
                """);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // ── Tests ───────────────────────────────────────────────────────────────────

    @Test
    void eachEmbeddedDef_becomesOwnUrnArtifact() {
        Map<String, JsonNode> stored = importAndCapture(catalog());
        for (String base : List.of("DefCatalog", "DefProduct", "DefCategory")) {
            String urn = urnOf(stored, base);
            assertThat(UrnParser.nameFromUrn(urn)).isEqualTo(base);
            assertThat(UrnParser.versionFromUrn(urn)).isNull();
            assertThat(UrnParser.disambiguatorFromUrn(urn)).isNotBlank();
        }
    }

    @Test
    void internalDefsRefs_rewrittenToUrns() {
        Map<String, JsonNode> stored = importAndCapture(catalog());
        // root: #/$defs/DefProduct → Product URN
        assertThat(elementOf(stored, "DefCatalog").path("properties").path("top").path("$ref").asText())
                .isEqualTo(urnOf(stored, "DefProduct"));
        // extracted Product: sibling #/$defs/DefCategory → Category URN
        assertThat(elementOf(stored, "DefProduct").path("properties").path("cat").path("$ref").asText())
                .isEqualTo(urnOf(stored, "DefCategory"));
    }

    @Test
    void redundantDefs_dropped_noDanglingPointers() {
        Map<String, JsonNode> stored = importAndCapture(catalog());
        assertThat(elementOf(stored, "DefCatalog").has("$defs")).isFalse();
        JsonNode product = elementOf(stored, "DefProduct");
        assertThat(product.has("$defs")).isFalse();
        assertThat(product.toString()).doesNotContain("#/$defs/");
    }

    @Test
    void unknownExtensionKeyword_xCorePrimaryKey_survivesImport() {
        // The downstream config-adapter (PostGIS/NiFi) derives the primary key from the custom
        // x-core-primaryKey keyword. The import transform (split $defs into Elements, rewrite
        // $ref to URNs, backfill $id/title, drop redundant $defs) must carry through any keyword
        // it does not itself manage — unchanged, on the root AND on every extracted def.
        JsonNode schema;
        try {
            schema = mapper.readTree("""
                {
                  "$schema":"https://json-schema.org/draft/2020-12/schema",
                  "title":"Station","type":"object",
                  "x-core-primaryKey":"stationId",
                  "properties":{"stationId":{"type":"string"},"sensor":{"$ref":"#/$defs/Sensor"}},
                  "$defs":{
                    "Sensor":{"type":"object","x-core-primaryKey":"sensorId","properties":{"sensorId":{"type":"string"}}}
                  }
                }
                """);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        Map<String, JsonNode> stored = importAndCapture(schema);

        assertThat(elementOf(stored, "Station").path("x-core-primaryKey").asText()).isEqualTo("stationId");
        assertThat(elementOf(stored, "Sensor").path("x-core-primaryKey").asText()).isEqualTo("sensorId");
    }

    @Test
    void datastructureRoot_storedAsDefsLibrary_membersSplitToElements() {
        // A datastructure-URN root that is a $defs library (no root shape) is stored as an ordinary
        // JSON-Schema $defs library of URN-$refs: each $defs member is split into a SEPARATE Element
        // artifact, and the DataStructure references it by URN under $defs (no type/properties of its
        // own). The DataStructure's pin is the import root (→ version.model_urn).
        JsonNode schema;
        try {
            schema = mapper.readTree("""
                {
                  "$schema":"https://json-schema.org/draft/2020-12/schema",
                  "$id":"urn:core:platform:civitas:datastructure:common:People:k4k6zhkb5b:1.0.0",
                  "title":"People",
                  "$defs": {
                    "Person": {
                      "$id":"urn:core:platform:civitas:element:common:Person:pp11abcd12:1.0.0",
                      "type":"object",
                      "properties":{"name":{"type":"string"}}
                    }
                  }
                }
                """);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        when(registry.storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class), any(VersionBump.class)))
            .thenReturn("urn:core:platform:civitas:element:common:Person:pp11abcd12:1.0.0");
        when(registry.storeDataStructure(anyString(), any(), any(VersionBump.class), nullable(String.class)))
            .thenAnswer(inv -> inv.getArgument(0) + ":1.0.0");

        SchemaImportResult result = svc.importSchema(new SchemaImportRequest(schema));

        assertThat(result.diagnostics()).isEmpty();
        // Root = the DataStructure (its pin → model_urn), not an Element.
        assertThat(UrnParser.artifactTypeFromUrn(result.resourceId())).isEqualTo("datastructure");

        // The member class is split out as a separate Element (keyed by its display name).
        ArgumentCaptor<String> elName = ArgumentCaptor.forClass(String.class);
        verify(registry).storeElement(elName.capture(), any(), anySet(), anySet(), nullable(String.class), any(VersionBump.class));
        assertThat(elName.getValue()).isEqualTo("Person");

        // The stored DataStructure is a $defs library of URN-$refs — no root shape.
        ArgumentCaptor<JsonNode> ds = ArgumentCaptor.forClass(JsonNode.class);
        verify(registry).storeDataStructure(anyString(), ds.capture(), any(VersionBump.class), nullable(String.class));
        JsonNode content = ds.getValue();
        assertThat(content.path("$schema").asText())
            .isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(content.path("$id").asText())
            .startsWith("urn:core:platform:civitas:datastructure:common:People:k4k6zhkb5b");
        assertThat(content.has("type")).isFalse();
        assertThat(content.has("properties")).isFalse();
        assertThat(content.path("$defs").path("Person").path("$ref").asText())
            .startsWith("urn:core:platform:civitas:element:common:Person");
    }

    /**
     * A member's authored {@code $id} version is a request, not an assignment — the registry decides
     * the version inside the write. The manifest must therefore reference the pin the write returned:
     * a ref carrying the authored version points at a version that may never exist, and the read then
     * resolves to an empty shell with a dangling {@code $ref}.
     */
    @Test
    void manifestReferencesTheVersionTheMemberWriteAssigned() {
        JsonNode schema = versionedDataStructureRootSchema();
        // The caller authored 2.0.0 throughout; the write assigns 1.0.1.
        when(registry.storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class), any(VersionBump.class)))
            .thenReturn("urn:core:platform:civitas:element:common:Person:pp11abcd12:1.0.1");
        when(registry.storeDataStructure(anyString(), any(), any(VersionBump.class), nullable(String.class)))
            .thenAnswer(inv -> inv.getArgument(0) + ":1.0.1");

        svc.importSchema(new SchemaImportRequest(schema));

        ArgumentCaptor<JsonNode> ds = ArgumentCaptor.forClass(JsonNode.class);
        verify(registry).storeDataStructure(anyString(), ds.capture(), any(VersionBump.class), nullable(String.class));
        JsonNode content = ds.getValue();
        assertThat(content.path("$defs").path("Person").path("$ref").asText())
            .isEqualTo("urn:core:platform:civitas:element:common:Person:pp11abcd12:1.0.1");
        // The root $ref designating one member as the root shape resolves to that same pin.
        assertThat(content.path("$ref").asText())
            .isEqualTo("urn:core:platform:civitas:element:common:Person:pp11abcd12:1.0.1");
    }

    /**
     * A member's stored content keeps a version-free {@code $id}. Stamping the assigned version into
     * it would change the document on every write, so every member would mint a version even when its
     * fields did not — defeating the registry's byte-identical short-circuit.
     */
    @Test
    void memberContentKeepsAVersionFreeId() {
        JsonNode schema = versionedDataStructureRootSchema();
        when(registry.storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class), any(VersionBump.class)))
            .thenReturn("urn:core:platform:civitas:element:common:Person:pp11abcd12:1.0.1");
        when(registry.storeDataStructure(anyString(), any(), any(VersionBump.class), nullable(String.class)))
            .thenAnswer(inv -> inv.getArgument(0) + ":1.0.1");

        svc.importSchema(new SchemaImportRequest(schema));

        ArgumentCaptor<JsonNode> member = ArgumentCaptor.forClass(JsonNode.class);
        verify(registry).storeElement(anyString(), member.capture(), anySet(), anySet(),
            nullable(String.class), any(VersionBump.class));
        assertThat(member.getValue().path("$id").asText())
            .isEqualTo("urn:core:platform:civitas:element:common:Person:pp11abcd12");

        ArgumentCaptor<JsonNode> ds = ArgumentCaptor.forClass(JsonNode.class);
        verify(registry).storeDataStructure(anyString(), ds.capture(), any(VersionBump.class), nullable(String.class));
        assertThat(ds.getValue().path("$id").asText())
            .isEqualTo("urn:core:platform:civitas:datastructure:common:People:k4k6zhkb5b");
    }

    /** A datastructure root whose members carry an authored version, and a root $ref to one of them. */
    private JsonNode versionedDataStructureRootSchema() {
        try {
            return mapper.readTree("""
                {
                  "$schema":"https://json-schema.org/draft/2020-12/schema",
                  "$id":"urn:core:platform:civitas:datastructure:common:People:k4k6zhkb5b:2.0.0",
                  "title":"People",
                  "$ref":"#/$defs/Person",
                  "$defs":{
                    "Person":{
                      "$id":"urn:core:platform:civitas:element:common:Person:pp11abcd12:2.0.0",
                      "type":"object",
                      "properties":{"name":{"type":"string"}}
                    }
                  }
                }
                """);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * The change class a caller requests must reach the registry, for the grouping and for its
     * member Elements alike: an Element is individually addressable, so a breaking change to one
     * must not be published as a patch just because it arrived inside a grouping. Members whose
     * content is unchanged mint no version at all — that is the registry's byte-identical
     * short-circuit and is unaffected by the bump.
     */
    @Test
    void requestedVersionBump_reachesTheGroupingAndItsMembers() {
        JsonNode schema = dataStructureRootSchema();
        when(registry.storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class),
            any(VersionBump.class)))
            .thenReturn("urn:core:platform:civitas:element:common:Person:pp11abcd12:2.0.0");
        when(registry.storeDataStructure(anyString(), any(), any(VersionBump.class), nullable(String.class)))
            .thenAnswer(inv -> inv.getArgument(0) + ":2.0.0");

        svc.importSchema(new SchemaImportRequest(schema, VersionBump.MAJOR));

        verify(registry).storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class),
            eq(VersionBump.MAJOR));
        verify(registry).storeDataStructure(anyString(), any(), eq(VersionBump.MAJOR), nullable(String.class));
    }

    /** An import that asks for nothing keeps Model Forge's existing default. */
    @Test
    void noRequestedBump_defaultsToPatch() {
        JsonNode schema = dataStructureRootSchema();
        when(registry.storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class),
            any(VersionBump.class)))
            .thenReturn("urn:core:platform:civitas:element:common:Person:pp11abcd12:1.0.1");
        when(registry.storeDataStructure(anyString(), any(), any(VersionBump.class), nullable(String.class)))
            .thenAnswer(inv -> inv.getArgument(0) + ":1.0.1");

        svc.importSchema(new SchemaImportRequest(schema));

        verify(registry).storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class),
            eq(VersionBump.PATCH));
        verify(registry).storeDataStructure(anyString(), any(), eq(VersionBump.PATCH), nullable(String.class));
    }

    /** A datastructure-rooted model with one member class. */
    private JsonNode dataStructureRootSchema() {
        try {
            return mapper.readTree("""
                {
                  "$schema":"https://json-schema.org/draft/2020-12/schema",
                  "$id":"urn:core:platform:civitas:datastructure:common:People:k4k6zhkb5b",
                  "title":"People",
                  "$defs":{
                    "Person":{
                      "$id":"urn:core:platform:civitas:element:common:Person:pp11abcd12",
                      "type":"object",
                      "properties":{"name":{"type":"string"}}
                    }
                  }
                }
                """);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void dependencyGraphEdges_created() {
        Map<String, JsonNode> stored = importAndCapture(catalog());
        // The graph keeps references verbatim — the import rewrites them to versioned CORE URNs.
        assertThat(graph.getDependencies(urnOf(stored, "DefCatalog"))).contains(urnOf(stored, "DefProduct"));
        assertThat(graph.getDependencies(urnOf(stored, "DefProduct"))).contains(urnOf(stored, "DefCategory"));
    }

    @Test
    void outgoingEdges_registerUnderTheRegistryAssignedPin_notThePreWriteId() {
        // The registry is the sole version authority: storeElement may return a version other than
        // the pre-write $id's (e.g. a bump on an existing artifact, or — as with the XÖV/XSD import
        // path — a caller-supplied version MF never honours, since a brand-new artifact is always
        // assigned 1.0.0). If the graph registered outgoing edges under the pre-write $id instead of
        // that returned pin, the node would be orphaned the moment rebuild() re-syncs from the
        // durable registry under the real pin — silently dropping the artifact's relations.
        JsonNode schema;
        try {
            schema = mapper.readTree("""
                {
                  "$schema":"https://json-schema.org/draft/2020-12/schema",
                  "$id":"urn:core:platform:civitas:element:common:Sensor:abc123:1.0.0",
                  "title":"Sensor","type":"object",
                  "properties":{"cat":{"$ref":"urn:core:platform:civitas:element:common:Category:def456:1.0.0"}}
                }
                """);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        String assignedPin = "urn:core:platform:civitas:element:common:Sensor:abc123:2.0.0";
        when(registry.storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class), any(VersionBump.class))).thenReturn(assignedPin);

        svc.importSchema(new SchemaImportRequest(schema));

        assertThat(graph.getDependencies(assignedPin))
            .contains("urn:core:platform:civitas:element:common:Category:def456:1.0.0");
    }

    @Test
    void defWithOwnUrnId_keptUnderThatUrn() {
        Map<String, JsonNode> stored = importAndCapture(catalog());
        // The def brings its own CORE-URN $id → it is kept verbatim (name "CustomSupplier",
        // not the def key "DefSupplier"), not re-minted.
        String supplierUrn = urnOf(stored, "CustomSupplier");
        assertThat(UrnParser.versionFromUrn(supplierUrn)).isNull();
        assertThat(stored.keySet())
            .noneMatch(urn -> "DefSupplier".equals(UrnParser.nameFromUrn(urn)));
        // and its sibling ref is also rewritten to the Category URN
        assertThat(stored.get(supplierUrn).path("properties").path("cat").path("$ref").asText())
                .isEqualTo(urnOf(stored, "DefCategory"));
    }

    @Test
    void subPathPointer_keepsDefsToStayResolvable() {
        JsonNode schema;
        try {
            schema = mapper.readTree("""
                {
                  "$schema":"https://json-schema.org/draft/2020-12/schema",
                  "title":"SubPathRoot","type":"object",
                  "properties":{"n":{"$ref":"#/$defs/Foo/properties/name"}},
                  "$defs":{"Foo":{"type":"object","properties":{"name":{"type":"string"}}}}
                }
                """);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        Map<String, JsonNode> stored = importAndCapture(schema);
        // A surviving sub-path pointer cannot be expressed as a whole-model_forge.artifact URN ref,
        // so $defs is retained to keep it resolvable rather than left dangling.
        JsonNode root = elementOf(stored, "SubPathRoot");
        assertThat(root.has("$defs")).isTrue();
        assertThat(root.path("properties").path("n").path("$ref").asText())
                .isEqualTo("#/$defs/Foo/properties/name");
    }

    // ── Minted identity (UUID invariant) ────────────────────────────────────────

    @Test
    void equalTitles_inSeparateImports_getDistinctIdentities() {
        Map<String, JsonNode> stored = importAndCapture(catalog());
        String firstRoot = urnOf(stored, "DefCatalog");
        clearInvocations(registry);

        Map<String, JsonNode> second = importAndCapture(catalog());
        String secondRoot = urnOf(second, "DefCatalog");

        // Same title, no $id → two independent artifacts, not two versions of one.
        assertThat(UrnParser.logicalUrn(secondRoot)).isNotEqualTo(UrnParser.logicalUrn(firstRoot));
        assertThat(UrnParser.logicalUrn(urnOf(second, "DefProduct")))
            .isNotEqualTo(UrnParser.logicalUrn(urnOf(stored, "DefProduct")));
    }

    @Test
    void reimportUnderMintedRootUrn_reusesItsSubElementIdentities() {
        Map<String, JsonNode> stored = importAndCapture(catalog());
        String rootUrn = urnOf(stored, "DefCatalog");
        String productUrn = urnOf(stored, "DefProduct");
        String logicalRoot = UrnParser.logicalUrn(rootUrn);
        // The host stores the logical root URN and passes it back as $id on follow-up versions;
        // the registry resolves it to the concrete current version.
        when(registry.resolveReference(logicalRoot)).thenReturn(Optional.of(rootUrn));
        clearInvocations(registry);

        Map<String, JsonNode> second = importAndCapture(catalogWithRootId(logicalRoot));

        // The re-imported defs keep the identities minted in the first import — the root is
        // versioned, its sub-elements are versioned along, nothing is duplicated.
        assertThat(urnOf(second, "DefProduct")).isEqualTo(productUrn);
        assertThat(second).containsKey(logicalRoot);
    }

    private JsonNode catalogWithRootId(String rootId) {
        var withId = (tools.jackson.databind.node.ObjectNode) catalog().deepCopy();
        withId.put("$id", rootId);
        return withId;
    }

    // ── DataStructure auto-creation on import ───────────────────────────────────────────

    @Test
    void import_createsDataStructureGroupingTheImportedStructures() {
        Map<String, JsonNode> stored = importAndCapture(catalog());
        String rootUrn = urnOf(stored, "DefCatalog");
        String rootName = UrnParser.nameFromUrn(rootUrn);
        String rootDisc = UrnParser.disambiguatorFromUrn(rootUrn);
        String dsLogical = "urn:core:platform:civitas:datastructure:common:" + rootName + ":" + rootDisc;

        ArgumentCaptor<String> urn = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> manifest = ArgumentCaptor.forClass(JsonNode.class);
        verify(registry).storeDataStructure(urn.capture(), manifest.capture(), any(VersionBump.class), nullable(String.class));

        // Stored under the logical datastructure URN that reuses the root Element's name AND
        // disambiguator segments (so re-imports version the same grouping).
        assertThat(urn.getValue()).isEqualTo(dsLogical);
        JsonNode m = manifest.getValue();
        assertThat(m.path("$schema").asText()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(m.path("$id").asText()).isEqualTo(dsLogical);
        // $defs IS the member list: every Element produced by the import (root + extracted $defs),
        // each as a $ref to its Element URN.
        assertThat(m.path("$defs").toString()).contains(
            urnOf(stored, "DefCatalog"),
            urnOf(stored, "DefProduct"),
            urnOf(stored, "DefCategory"),
            urnOf(stored, "CustomSupplier"));
    }

    // ── Raw-XSD import: version guard (URN-injection hardening) ───────────────────

    @Test
    void importXsd_acceptsTwoSegmentXoevVersion() {
        // Real XRepository/XÖV standards carry two-segment versions (e.g. 6.0, 5.5). They must
        // import cleanly — the version guard only blocks injection chars, not short versions.
        SchemaImportResult r = svc.importXsd(new XsdImportRequest("xplanung", "6.0",
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>", null));

        assertThat(r.diagnostics()).isEmpty();
        // Name-derived identity → minted with a UUID; the version segment stays the client's.
        assertThat(UrnParser.nameFromUrn(r.resourceId())).isEqualTo("xplanung");
        assertThat(UrnParser.versionFromUrn(r.resourceId())).isEqualTo("6.0");
        assertThat(UrnParser.disambiguatorFromUrn(r.resourceId())).isNotBlank();
    }

    @Test
    void importXsd_rejectsColonBearingVersionAsInjection() {
        // A ':' in the version would inject extra colon-delimited URN segments — still rejected.
        SchemaImportResult r = svc.importXsd(new XsdImportRequest("xplanung", "6.0:evil",
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>", null));

        assertThat(r.resourceId()).isNull();
        assertThat(r.diagnostics()).extracting(d -> d.code()).contains("invalid-version");
        verify(registry, never()).storeXsdElement(anyString(), anyString(), anySet());
    }

    // ── $defs $id that collides with the reserved root URN ────────────────────────

    @Test
    void defWhoseIdReusesRootUrn_doesNotCollideOntoRoot() {
        // A $defs entry whose explicit $id equals the root's URN must NOT be stored under the root's
        // logical URN (which would silently mint a second version of the root's artifact). It falls
        // through to pass-2 key derivation and gets a distinct URN derived from its def key.
        JsonNode schema;
        try {
            schema = mapper.readTree("""
                {
                  "$schema":"https://json-schema.org/draft/2020-12/schema",
                  "$id":"urn:core:platform:civitas:element:common:Root:wn0maelckn:1.0.0",
                  "title":"Root","type":"object",
                  "properties":{"x":{"$ref":"#/$defs/Twin"}},
                  "$defs":{
                    "Twin":{"$id":"urn:core:platform:civitas:element:common:Root:wn0maelckn:9.9.9","type":"object","properties":{"y":{"type":"string"}}}
                  }
                }
                """);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        Map<String, JsonNode> stored = importAndCapture(schema);

        // Root keeps its own URN; the colliding def did NOT overwrite or duplicate the root's logical
        // model_forge.artifact — it received a distinct, minted URN derived from its def key ("Twin").
        String rootUrn = urnOf(stored, "Root");
        assertThat(UrnParser.versionFromUrn(rootUrn)).isNull();
        assertThat(urnOf(stored, "Twin")).isNotEqualTo(rootUrn);
        assertThat(UrnParser.nameFromUrn(urnOf(stored, "Twin"))).isEqualTo("Twin");
    }

    // ── x-core-ref foreign-key existence: co-import allow-set ─────────────────────

    @Test
    void coImportedXCoreRefTarget_isAcceptedWithoutConsultingTheRegistry() {
        // Baum has a concrete x-core-ref FK to a sibling Strasse (a $defs entry created in the same
        // request) and a self-reference to Baum. Both targets carry explicit $ids (an authored
        // x-core-ref can only name URNs the author controls — minted URNs are not predictable) and
        // are co-imported, so the existence check must treat them as present — no diagnostics, and
        // resolveReference is never called.
        JsonNode schema;
        try {
            schema = mapper.readTree("""
                {
                  "$schema":"https://json-schema.org/draft/2020-12/schema",
                  "$id":"urn:core:platform:civitas:element:common:Baum:vd1c1ziwlu:1.0.0",
                  "title":"Baum","type":"object",
                  "properties":{
                    "stehtAn":{"type":"string","x-core-ref":{"type":"urn:core:platform:civitas:element:common:Strasse:u8pwgr2zzg:1.0.0"}},
                    "self":{"type":"string","x-core-ref":{"type":"urn:core:platform:civitas:element:common:Baum:vd1c1ziwlu:1.0.0"}}
                  },
                  "$defs":{
                    "Strasse":{"$id":"urn:core:platform:civitas:element:common:Strasse:u8pwgr2zzg:1.0.0","type":"object","properties":{"name":{"type":"string"}}}
                  }
                }
                """);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        SchemaImportResult response = svc.importSchema(new SchemaImportRequest(schema));

        assertThat(response.diagnostics()).isEmpty();
        // The FK targets were satisfied from the co-import allow-set — the existence check never
        // resolved the sibling target against the registry. (The root URN itself is looked up
        // once by the sub-element-reuse matching; that lookup is not the FK existence check.)
        verify(registry, never()).resolveReference("urn:core:platform:civitas:element:common:Strasse:u8pwgr2zzg:1.0.0");
    }

    @Test
    void xCoreRefAssociations_becomeDependencyGraphEdgesLikeCompositionRefs() {
        // An association (x-core-ref) is by-reference, not by-value — but it still names a real
        // artifact dependency, so it must be navigable via dependencies()/dependents() exactly like
        // a $ref composition edge. Reuses the Baum/Strasse fixture (self-ref included).
        JsonNode schema;
        try {
            schema = mapper.readTree("""
                {
                  "$schema":"https://json-schema.org/draft/2020-12/schema",
                  "$id":"urn:core:platform:civitas:element:common:Baum:vd1c1ziwlu:1.0.0",
                  "title":"Baum","type":"object",
                  "properties":{
                    "stehtAn":{"type":"string","x-core-ref":{"type":"urn:core:platform:civitas:element:common:Strasse:u8pwgr2zzg:1.0.0"}},
                    "self":{"type":"string","x-core-ref":{"type":"urn:core:platform:civitas:element:common:Baum:vd1c1ziwlu:1.0.0"}}
                  },
                  "$defs":{
                    "Strasse":{"$id":"urn:core:platform:civitas:element:common:Strasse:u8pwgr2zzg:1.0.0","type":"object","properties":{"name":{"type":"string"}}}
                  }
                }
                """);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        // Graph nodes are keyed by the pin the write assigns, so the write has to report one.
        when(registry.storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class), any(VersionBump.class)))
            .thenAnswer(inv -> inv.getArgument(1, JsonNode.class).path("$id").asText() + ":1.0.0");

        svc.importSchema(new SchemaImportRequest(schema));

        String baumUrn = "urn:core:platform:civitas:element:common:Baum:vd1c1ziwlu:1.0.0";
        assertThat(graph.getDependencies(baumUrn)).contains(
            "urn:core:platform:civitas:element:common:Strasse:u8pwgr2zzg:1.0.0", baumUrn);
        assertThat(graph.getDependents("urn:core:platform:civitas:element:common:Strasse:u8pwgr2zzg:1.0.0"))
            .contains(baumUrn);
    }

    // ── Pure $defs container: a document with no shape of its own ────────────────

    @Test
    void topLevelWithNoOwnShape_becomesNoElementOfItsOwn_onlyItsDefsDo() {
        // A document that is only metadata + $defs (no type/properties/$ref/…) is a pure container
        // — the DataStructure grouping its defs, not a tenth Element in its own right.
        JsonNode schema;
        try {
            schema = mapper.readTree("""
                {
                  "$schema":"https://json-schema.org/draft/2020-12/schema",
                  "title":"Bundle",
                  "$defs":{
                    "Alpha":{"type":"object","properties":{"name":{"type":"string"}}},
                    "Beta":{"type":"object","properties":{"name":{"type":"string"}}}
                  }
                }
                """);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        Map<String, JsonNode> stored = importAndCapture(schema);

        assertThat(stored).hasSize(2);
        assertThat(stored.keySet()).noneMatch(urn -> "Bundle".equals(UrnParser.nameFromUrn(urn)));
        assertThat(UrnParser.nameFromUrn(urnOf(stored, "Alpha"))).isEqualTo("Alpha");
        assertThat(UrnParser.nameFromUrn(urnOf(stored, "Beta"))).isEqualTo("Beta");

        // The DataStructure still groups every def (as $defs member $refs), borrowing the first def's identity.
        ArgumentCaptor<JsonNode> manifest = ArgumentCaptor.forClass(JsonNode.class);
        verify(registry).storeDataStructure(anyString(), manifest.capture(), any(VersionBump.class), nullable(String.class));
        assertThat(manifest.getValue().path("$defs").toString())
            .contains(urnOf(stored, "Alpha"), urnOf(stored, "Beta"));
    }

    // ── DataStructure contract enforcement ──────────────────────────────────────

    private static final String DS_ROOT_URN =
        "urn:core:platform:civitas:datastructure:sta:AirQuality:abcdefghij:1.0.0";

    @Test
    void dataStructureRoot_withARootRefThatIsNotAnElementUrn_isRejectedAndStoresNothing() {
        // A root $ref that is neither a resolvable "#/$defs/<Name>" pointer nor an Element URN is
        // carried into the stored document verbatim, so the CORE schema's Element-URN pattern is
        // the only thing standing between a caller and a DataStructure whose entry point does not
        // exist. That pattern only applies when the DataStructure is validated by kind.
        JsonNode schema = mapper.readTree("""
            { "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "%s",
              "title": "AirQuality",
              "$ref": "http://169.254.169.254/creds",
              "$defs": {
                "Reading": { "type": "object", "properties": { "no2": { "type": "number" } } }
              } }
            """.formatted(DS_ROOT_URN));

        SchemaImportResult result = svc.importSchema(new SchemaImportRequest(schema));

        assertThat(result.diagnostics()).isNotEmpty();
        verify(registry, never()).storeDataStructure(anyString(), any(), any(VersionBump.class), nullable(String.class));
        verify(registry, never()).storeElement(
            anyString(), any(), anySet(), anySet(), nullable(String.class), any(VersionBump.class));
    }

    @Test
    void dataStructureRoot_withALocalRootRef_isAccepted() {
        // The shape the platform produces must keep importing: the root $ref is a local pointer,
        // resolved to the member's minted Element URN.
        JsonNode schema = mapper.readTree("""
            { "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "%s",
              "title": "AirQuality",
              "$ref": "#/$defs/Reading",
              "$defs": {
                "Reading": { "type": "object", "properties": { "no2": { "type": "number" } } }
              } }
            """.formatted(DS_ROOT_URN));

        assertThat(svc.importSchema(new SchemaImportRequest(schema)).diagnostics()).isEmpty();
    }

    // ── Derived identity for externally-identified imports ──────────────────────

    @Test
    void keyedImport_derivesTheSameUrnForTheSameUpstreamKey() {
        String url = "https://raw.githubusercontent.com/x/schema.json";
        when(remoteFetcher.fetchJson(url)).thenReturn(mapper.readTree("""
            { "title": "WeatherObserved", "type": "object",
              "properties": { "temperature": {"type": "number"} } }
            """));

        String first = importedRootUrn(url, "smart-data-models:Weather/WeatherObserved");
        clearInvocations(registry);
        String second = importedRootUrn(url, "smart-data-models:Weather/WeatherObserved");

        // Same upstream key → same logical URN, so the registry versions the artifact instead of
        // creating an unrelated duplicate. A random disambiguator could never do this.
        assertThat(UrnParser.logicalUrn(second)).isEqualTo(UrnParser.logicalUrn(first));
    }

    @Test
    void keyedImport_keepsDifferentUpstreamsApartDespiteTheSameEntityName() {
        String url = "https://raw.githubusercontent.com/x/schema.json";
        when(remoteFetcher.fetchJson(url)).thenReturn(mapper.readTree("""
            { "title": "WeatherObserved", "type": "object" }
            """));

        String weather = importedRootUrn(url, "smart-data-models:Weather/WeatherObserved");
        clearInvocations(registry);
        String environment = importedRootUrn(url, "smart-data-models:Environment/WeatherObserved");

        assertThat(UrnParser.logicalUrn(environment)).isNotEqualTo(UrnParser.logicalUrn(weather));
    }

    @Test
    void keyedImport_leavesAnExistingCoreUrnIdentityAlone() {
        String url = "https://raw.githubusercontent.com/x/schema.json";
        String ownId = "urn:core:platform:civitas:element:common:Given:abcdefghij:1.0.0";
        String ownLogical = "urn:core:platform:civitas:element:common:Given:abcdefghij";
        when(remoteFetcher.fetchJson(url)).thenReturn(mapper.readTree("""
            { "$id": "%s", "title": "Given", "type": "object" }
            """.formatted(ownId)));

        assertThat(importedRootUrn(url, "smart-data-models:Weather/Given")).isEqualTo(ownLogical);
    }

    /** The $id of the first Element the keyed import stored. */
    private String importedRootUrn(String url, String stableKey) {
        svc.importFromUrl(url, stableKey);
        ArgumentCaptor<JsonNode> stored = ArgumentCaptor.forClass(JsonNode.class);
        verify(registry, atLeastOnce()).storeElement(
            anyString(), stored.capture(), anySet(), anySet(), nullable(String.class), any(VersionBump.class));
        return stored.getAllValues().getFirst().path("$id").asText(null);
    }

    // ── Atomicity ───────────────────────────────────────────────────────────────

    @Test
    void multiElementImport_runsInsideOneRegistryTransaction() {
        JsonNode schema = mapper.readTree("""
            { "title": "Bundle",
              "$defs": { "Alpha": {"type":"object"}, "Beta": {"type":"object"} } }
            """);

        svc.importSchema(new SchemaImportRequest(schema));

        // One transaction spanning both Elements and the grouping manifest — not one per write.
        verify(registry).inTransaction(any());
    }

    @Test
    void whenALaterElementFails_noGraphNodeIsPublishedForTheEarlierOnes() {
        DependencyGraphService graphSpy = mock(DependencyGraphService.class);
        SchemaImportService service = new SchemaImportService(
            validatorAcceptingEverything(), mapper, registry,
            new UrnService("platform", "civitas", "common", "1.0.0"),
            new SchemaRefExtractor(), graphSpy,
            new ReferenceExistenceValidator(registry, new SchemaRefExtractor()),
            mock(RemoteSchemaRepository.class), new CoreSchemaValidator(mapper));

        // First Element stores fine; the second is rejected by the registry mid-import.
        when(registry.storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class), any(VersionBump.class)))
            .thenReturn("urn:core:platform:civitas:element:common:Alpha:aaaaaaaaaa:1.0.0")
            .thenThrow(new IllegalArgumentException("rejected"));

        JsonNode schema = mapper.readTree("""
            { "title": "Bundle",
              "$defs": { "Alpha": {"type":"object"}, "Beta": {"type":"object"} } }
            """);

        assertThatThrownBy(() -> service.importSchema(new SchemaImportRequest(schema)))
            .isInstanceOf(IllegalArgumentException.class);

        // The durable write rolls back, and the in-memory graph — which does not roll back — must
        // therefore never have been touched, or the failed import leaves phantom nodes behind.
        verify(graphSpy, never()).register(anyString(), anySet());
        verify(graphSpy, never()).registerFromRegistry(anyString());
    }

    private ModelValidator validatorAcceptingEverything() {
        ModelValidator validator = mock(ModelValidator.class);
        when(validator.validateSchema(any())).thenReturn(List.of());
        return validator;
    }

    /**
     * A root {@code $ref} may name its member by CORE URN rather than by a local {@code #/$defs/}
     * pointer, and the schema allows that URN to carry a version. It is repinned like any other
     * member reference — the version a caller writes there is no more authoritative than one in an
     * {@code $id}.
     */
    @Test
    void rootRefGivenAsAUrn_isRepinnedToTheAssignedVersion() {
        JsonNode schema;
        try {
            schema = mapper.readTree("""
                {
                  "$schema":"https://json-schema.org/draft/2020-12/schema",
                  "$id":"urn:core:platform:civitas:datastructure:common:People:k4k6zhkb5b",
                  "title":"People",
                  "$ref":"urn:core:platform:civitas:element:common:Person:pp11abcd12:2.0.0",
                  "$defs":{
                    "Person":{
                      "$id":"urn:core:platform:civitas:element:common:Person:pp11abcd12",
                      "type":"object",
                      "properties":{"name":{"type":"string"}}
                    }
                  }
                }
                """);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        when(registry.storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class), any(VersionBump.class)))
            .thenReturn("urn:core:platform:civitas:element:common:Person:pp11abcd12:1.0.1");
        when(registry.storeDataStructure(anyString(), any(), any(VersionBump.class), nullable(String.class)))
            .thenAnswer(inv -> inv.getArgument(0) + ":1.0.1");

        svc.importSchema(new SchemaImportRequest(schema));

        ArgumentCaptor<JsonNode> ds = ArgumentCaptor.forClass(JsonNode.class);
        verify(registry).storeDataStructure(anyString(), ds.capture(), any(VersionBump.class), nullable(String.class));
        assertThat(ds.getValue().path("$ref").asText())
            .isEqualTo("urn:core:platform:civitas:element:common:Person:pp11abcd12:1.0.1");
    }

    /**
     * The grouping a plain {@code $defs} schema gets automatically references the versions its member
     * writes assigned, exactly as a datastructure-rooted import does — the two manifests are built by
     * different code but carry the same guarantee.
     */
    @Test
    void automaticGroupingReferencesTheVersionsTheMemberWritesAssigned() {
        when(registry.storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class), any(VersionBump.class)))
            .thenAnswer(inv -> inv.getArgument(1, JsonNode.class).path("$id").asText() + ":3.1.4");
        when(registry.storeDataStructure(anyString(), any(), any(VersionBump.class), nullable(String.class)))
            .thenAnswer(inv -> inv.getArgument(0) + ":3.1.4");

        svc.importSchema(new SchemaImportRequest(catalog()));

        ArgumentCaptor<JsonNode> ds = ArgumentCaptor.forClass(JsonNode.class);
        verify(registry).storeDataStructure(anyString(), ds.capture(), any(VersionBump.class), nullable(String.class));
        JsonNode defs = ds.getValue().path("$defs");
        assertThat(defs.isObject()).isTrue();
        assertThat(defs.properties()).isNotEmpty();
        defs.properties().forEach(e ->
            assertThat(UrnParser.versionFromUrn(e.getValue().path("$ref").asText())).isEqualTo("3.1.4"));
    }
}
