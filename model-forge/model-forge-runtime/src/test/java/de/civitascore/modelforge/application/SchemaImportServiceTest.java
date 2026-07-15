package de.civitascore.modelforge.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.core.port.RemoteSchemaRepository;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.urn.UrnService;
import de.civitascore.modelforge.validation.ModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
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
    private DependencyGraphService graph;
    private SchemaImportService svc;

    @BeforeEach
    void setUp() {
        registry = mock(ArtifactRegistry.class);
        when(registry.resolveReference(anyString())).thenReturn(Optional.empty());
        ModelValidator validator = mock(ModelValidator.class);
        when(validator.validateSchema(any())).thenReturn(List.of());
        UrnService urns = new UrnService("platform", "civitas", "common", "1.0.0");
        SchemaRefExtractor refExtractor = new SchemaRefExtractor();
        graph = new DependencyGraphService(registry);
        ReferenceExistenceValidator refExistence = new ReferenceExistenceValidator(registry, refExtractor);
        RemoteSchemaRepository remoteFetcher = mock(RemoteSchemaRepository.class);
        svc = new SchemaImportService(validator, mapper, registry,
                urns, refExtractor, graph, refExistence, remoteFetcher);
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
        verify(registry, atLeastOnce()).storeElement(name.capture(), body.capture(), anySet(), anySet(), nullable(String.class));
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
            assertThat(UrnParser.versionFromUrn(urn)).isEqualTo("1.0.0");
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
        when(registry.storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class))).thenReturn(assignedPin);

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
        assertThat(UrnParser.versionFromUrn(supplierUrn)).isEqualTo("1.0.0");
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
        verify(registry).storeDataStructure(urn.capture(), manifest.capture());

        // Stored under the logical datastructure URN that reuses the root Element's name AND
        // disambiguator segments (so re-imports version the same grouping).
        assertThat(urn.getValue()).isEqualTo(dsLogical);
        JsonNode m = manifest.getValue();
        assertThat(m.path("$schema").asText()).isEqualTo("https://civitasconnect.digital/core-datastructure/v1");
        assertThat(m.path("id").asText()).isEqualTo(dsLogical + ":1.0.0");
        // Groups every Element produced by the import (root + extracted $defs).
        assertThat(m.path("elementRefs").toString()).contains(
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
        assertThat(UrnParser.versionFromUrn(rootUrn)).isEqualTo("1.0.0");
        assertThat(stored.keySet()).noneMatch(u -> "9.9.9".equals(UrnParser.versionFromUrn(u)));
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

        // The DataStructure still groups every def, borrowing the first def's identity.
        ArgumentCaptor<JsonNode> manifest = ArgumentCaptor.forClass(JsonNode.class);
        verify(registry).storeDataStructure(anyString(), manifest.capture());
        assertThat(manifest.getValue().path("elementRefs").toString())
            .contains(urnOf(stored, "Alpha"), urnOf(stored, "Beta"));
    }
}
