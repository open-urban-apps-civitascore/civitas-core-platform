package de.civitascore.modelforge.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for ViewService — no Spring context.
 *
 * Test data uses a 3-schema dependency chain that mirrors the city model:
 *
 *   Observation  →  SensorReading  →  GeoPoint
 *
 * Observation and SensorReading each hold a $ref to the next schema.
 * GeoPoint is a leaf with only scalar properties.
 */
class ViewServiceTest {

    // ── URNs ──────────────────────────────────────────────────────────────────

    private static final String OBS_URN    = "urn:core:platform:civitas:element:common:Observation:ulhry9fjx6:1.0.0";
    private static final String SENSOR_URN = "urn:core:platform:civitas:element:common:SensorReading:d28s38wfmi:1.0.0";
    private static final String GEO_URN    = "urn:core:platform:civitas:element:common:GeoPoint:3ak90vqrog:1.0.0";

    // ── Schemas ───────────────────────────────────────────────────────────────

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** GeoPoint — leaf schema, no $ref */
    private static final JsonNode GEO_SCHEMA;
    static {
        ObjectNode props = MAPPER.createObjectNode();
        props.set("lat", MAPPER.createObjectNode().put("type", "number"));
        props.set("lon", MAPPER.createObjectNode().put("type", "number"));
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("$id", GEO_URN);
        schema.put("title", "GeoPoint");
        schema.put("type", "object");
        schema.put("description", "WGS84 coordinate");
        schema.set("properties", props);
        GEO_SCHEMA = schema;
    }

    /** SensorReading — has a $ref to GeoPoint in "location" property */
    private static final JsonNode SENSOR_SCHEMA;
    static {
        ObjectNode props = MAPPER.createObjectNode();
        props.set("sensorId",    MAPPER.createObjectNode().put("type", "string"));
        props.set("temperature", MAPPER.createObjectNode().put("type", "number"));
        props.set("location",    MAPPER.createObjectNode()
                .<ObjectNode>put("$ref", GEO_URN)
                .put("description", "Sensor position"));

        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("$id", SENSOR_URN);
        schema.put("title", "SensorReading");
        schema.put("type", "object");
        schema.putArray("required").add("sensorId").add("temperature");
        schema.set("properties", props);
        SENSOR_SCHEMA = schema;
    }

    /** Observation — has a $ref to SensorReading in "reading" property */
    private static final JsonNode OBS_SCHEMA;
    static {
        ObjectNode props = MAPPER.createObjectNode();
        props.set("id",         MAPPER.createObjectNode().put("type", "string"));
        props.set("observedAt", MAPPER.createObjectNode().put("type", "string").put("format", "date-time"));
        props.set("value",      MAPPER.createObjectNode().put("type", "number"));
        props.set("reading",    MAPPER.createObjectNode().put("$ref", SENSOR_URN));

        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("$id", OBS_URN);
        schema.put("title", "Observation");
        schema.put("type", "object");
        schema.set("properties", props);
        OBS_SCHEMA = schema;
    }

    // ── Test infrastructure ───────────────────────────────────────────────────

    private ArtifactRegistry       mockRegistry;
    private DependencyGraphService graph;
    private ViewService          viewService;

    @BeforeEach
    void setUp() {
        mockRegistry = mock(ArtifactRegistry.class);
        // DependencyGraphService without Registry; we populate the graph manually via register()
        graph = new DependencyGraphService(mock(de.civitascore.modelforge.core.port.ArtifactRegistry.class));
        viewService = new ViewService(mockRegistry, graph, MAPPER);

        // Stub both versioned URNs (used by inline) and logical URNs
        // (used by bundle when iterating dependency graph entries).
        String geoL    = "urn:core:platform:civitas:element:common:GeoPoint:3ak90vqrog";
        String sensorL = "urn:core:platform:civitas:element:common:SensorReading:d28s38wfmi";
        String obsL    = "urn:core:platform:civitas:element:common:Observation:ulhry9fjx6";
        when(mockRegistry.fetchElementOrXsd(GEO_URN)).thenReturn(Optional.of(GEO_SCHEMA));
        when(mockRegistry.fetchElementOrXsd(geoL)).thenReturn(Optional.of(GEO_SCHEMA));
        when(mockRegistry.fetchElementOrXsd(SENSOR_URN)).thenReturn(Optional.of(SENSOR_SCHEMA));
        when(mockRegistry.fetchElementOrXsd(sensorL)).thenReturn(Optional.of(SENSOR_SCHEMA));
        when(mockRegistry.fetchElementOrXsd(OBS_URN)).thenReturn(Optional.of(OBS_SCHEMA));
        when(mockRegistry.fetchElementOrXsd(obsL)).thenReturn(Optional.of(OBS_SCHEMA));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Logical (version-free) form of a CORE URN — drops the trailing :version segment. */
    private static String logical(String urn) {
        return urn.substring(0, urn.lastIndexOf(':'));
    }

    /**
     * Stub an object schema (with $id and the given properties) for both its versioned
     * and logical URN, mirroring how bundle() fetches the root (versioned) and graph
     * dependencies (logical).
     */
    private void stubObj(String urn, java.util.function.Consumer<ObjectNode> propsBuilder) {
        ObjectNode props = MAPPER.createObjectNode();
        propsBuilder.accept(props);
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("$id", urn);
        schema.put("type", "object");
        schema.set("properties", props);
        when(mockRegistry.fetchElementOrXsd(urn)).thenReturn(Optional.of(schema));
        when(mockRegistry.fetchElementOrXsd(logical(urn))).thenReturn(Optional.of(schema));
    }

    /** Recursively collect every {@code $id} value in the document. */
    private static void collectIds(JsonNode node, Set<String> acc) {
        if (node == null) return;
        if (node.isObject()) {
            String id = node.path("$id").asText(null);
            if (id != null && !id.isBlank()) acc.add(id);
            node.properties().forEach(e -> collectIds(e.getValue(), acc));
        } else if (node.isArray()) {
            node.forEach(child -> collectIds(child, acc));
        }
    }

    /** Recursively collect every {@code $ref} value that is a CORE URN. */
    private static void collectCoreUrnRefs(JsonNode node, Set<String> acc) {
        if (node == null) return;
        if (node.isObject()) {
            String ref = node.path("$ref").asText(null);
            if (ref != null && ref.startsWith("urn:core:")) acc.add(ref);
            node.properties().forEach(e -> collectCoreUrnRefs(e.getValue(), acc));
        } else if (node.isArray()) {
            node.forEach(child -> collectCoreUrnRefs(child, acc));
        }
    }

    /** Recursively collect every {@code x-core-primaryKey} value in the document. */
    private static void collectPrimaryKeys(JsonNode node, Set<String> acc) {
        if (node == null) return;
        if (node.isObject()) {
            String pk = node.path("x-core-primaryKey").asText(null);
            if (pk != null && !pk.isBlank()) acc.add(pk);
            node.properties().forEach(e -> collectPrimaryKeys(e.getValue(), acc));
        } else if (node.isArray()) {
            node.forEach(child -> collectPrimaryKeys(child, acc));
        }
    }

    /** A leaf object schema carrying an {@code x-core-primaryKey}, stubbed under {@code urn}. */
    private ObjectNode stubWithPrimaryKey(String urn, String primaryKey) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("$id", urn);
        schema.put("type", "object");
        schema.put("x-core-primaryKey", primaryKey);
        schema.set("properties", MAPPER.createObjectNode()
            .set(primaryKey, MAPPER.createObjectNode().put("type", "string")));
        when(mockRegistry.fetchElementOrXsd(urn)).thenReturn(Optional.of(schema));
        when(mockRegistry.fetchElementOrXsd(logical(urn))).thenReturn(Optional.of(schema));
        return schema;
    }

    // ── config-adapter fidelity: unknown extension keywords survive derived views ───────

    @Test
    void inline_preservesUnknownExtensionKeyword_xCorePrimaryKey() {
        // The config-adapter contract (PostGIS/NiFi read x-core-primaryKey): a derived view must
        // carry the keyword through unchanged — on the root AND on an inlined dependency.
        String siteUrn = "urn:core:platform:civitas:element:common:Site:yddl6euvis:1.0.0";
        stubWithPrimaryKey(GEO_URN, "geoId");
        ObjectNode site = MAPPER.createObjectNode();
        site.put("$id", siteUrn);
        site.put("type", "object");
        site.put("x-core-primaryKey", "siteId");
        site.set("properties", MAPPER.createObjectNode()
            .set("location", MAPPER.createObjectNode().put("$ref", GEO_URN)));
        when(mockRegistry.fetchElementOrXsd(siteUrn)).thenReturn(Optional.of(site));

        JsonNode deref = viewService.inline(siteUrn).orElseThrow();

        assertThat(deref.path("x-core-primaryKey").asText()).isEqualTo("siteId");
        // The inlined GeoPoint replaces the $ref at properties.location and keeps its own keyword.
        assertThat(deref.path("properties").path("location").path("x-core-primaryKey").asText())
            .isEqualTo("geoId");
    }

    @Test
    void bundle_preservesUnknownExtensionKeyword_xCorePrimaryKey() {
        String siteUrn = "urn:core:platform:civitas:element:common:Site:yddl6euvis:1.0.0";
        stubWithPrimaryKey(GEO_URN, "geoId");
        ObjectNode site = MAPPER.createObjectNode();
        site.put("$id", siteUrn);
        site.put("type", "object");
        site.put("x-core-primaryKey", "siteId");
        site.set("properties", MAPPER.createObjectNode()
            .set("location", MAPPER.createObjectNode().put("$ref", GEO_URN)));
        when(mockRegistry.fetchElementOrXsd(siteUrn)).thenReturn(Optional.of(site));
        graph.register(siteUrn, Set.of(GEO_URN));

        JsonNode bundled = viewService.bundle(siteUrn, 5).orElseThrow();

        // Root keeps its keyword; the embedded GeoPoint (under $defs) keeps its own.
        assertThat(bundled.path("x-core-primaryKey").asText()).isEqualTo("siteId");
        Set<String> primaryKeys = new java.util.LinkedHashSet<>();
        collectPrimaryKeys(bundled, primaryKeys);
        assertThat(primaryKeys).contains("siteId", "geoId");
    }

    // ── latest-ref rewrite in derived views (M4) ───────────────────────────────

    @Test
    void latestRef_isRewrittenToConcreteVersionInViews() {
        String geoLatest = "urn:core:platform:civitas:element:common:GeoPoint:3ak90vqrog:latest";
        String trackerUrn = "urn:core:platform:civitas:element:common:Tracker:tr9g67qgnh:1.0.0";
        ObjectNode root = MAPPER.createObjectNode();
        root.put("$id", trackerUrn);
        root.put("type", "object");
        root.set("properties", MAPPER.createObjectNode()
            .set("location", MAPPER.createObjectNode().put("$ref", geoLatest)));
        when(mockRegistry.fetchElementOrXsd(trackerUrn)).thenReturn(Optional.of(root));
        when(mockRegistry.fetchElementOrXsd(geoLatest)).thenReturn(Optional.of(GEO_SCHEMA));
        // ':latest' resolves to the current concrete version.
        when(mockRegistry.resolveReference(geoLatest)).thenReturn(Optional.of(GEO_URN));

        // Inlined with no inlining (depth 0): the ref is left in place but rewritten to the
        // concrete version, so the output carries no unresolved ':latest' pointer.
        JsonNode deref = viewService.inline(trackerUrn, 0).orElseThrow();
        assertThat(deref.path("properties").path("location").path("$ref").asText()).isEqualTo(GEO_URN);

        // Bundled: the root ref is rewritten to the concrete version, matching the embedded $id.
        graph.register(trackerUrn, Set.of(geoLatest));
        JsonNode bundled = viewService.bundle(trackerUrn, 5).orElseThrow();
        Set<String> refs = new java.util.LinkedHashSet<>();
        collectCoreUrnRefs(bundled, refs);
        assertThat(refs).contains(GEO_URN).doesNotContain(geoLatest);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Inline view
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    class InlineTests {

        @Test
        void registryAbsent_returnsEmpty() {
            ViewService noRegistry = new ViewService(mock(de.civitascore.modelforge.core.port.ArtifactRegistry.class), graph, MAPPER);
            assertThat(noRegistry.inline(SENSOR_URN)).isEmpty();
        }

        @Test
        void unknownUrn_returnsEmpty() {
            when(mockRegistry.fetchElementOrXsd("urn:core:unknown")).thenReturn(Optional.empty());
            assertThat(viewService.inline("urn:core:unknown")).isEmpty();
        }

        @Test
        void leafSchema_noRefs_returnsUnchanged() {
            Optional<JsonNode> result = viewService.inline(GEO_URN);

            assertThat(result).isPresent();
            assertThat(result.get().path("title").asText()).isEqualTo("GeoPoint");
            assertThat(result.get().path("properties").has("lat")).isTrue();
        }

        @Test
        void schemaWithRef_inlinesRefSchema() {
            Optional<JsonNode> result = viewService.inline(SENSOR_URN);

            assertThat(result).isPresent();
            JsonNode location = result.get().path("properties").path("location");
            // The $ref should be replaced with the GeoPoint schema content
            assertThat(location.path("$ref").isMissingNode()).isTrue();
            assertThat(location.path("title").asText()).isEqualTo("GeoPoint");
            assertThat(location.path("properties").has("lat")).isTrue();
        }

        @Test
        void siblingKeywordsNextToRef_arePreserved() {
            // SensorReading.location is { "$ref": GEO_URN, "description": "Sensor position" }.
            // The local description must survive (bug guard: it used to be dropped), while the
            // referenced GeoPoint schema is inlined.
            Optional<JsonNode> result = viewService.inline(SENSOR_URN);

            assertThat(result).isPresent();
            JsonNode location = result.get().path("properties").path("location");
            assertThat(location.path("$ref").isMissingNode()).isTrue();
            // inlined target content present
            assertThat(location.path("title").asText()).isEqualTo("GeoPoint");
            assertThat(location.path("properties").has("lat")).isTrue();
            // local sibling keyword preserved
            assertThat(location.path("description").asText()).isEqualTo("Sensor position");
        }

        @Test
        void siblingKeyword_winsOnCollisionWithTarget() {
            // A property that both $refs GeoPoint AND overrides its title locally.
            // The local title must win; GeoPoint's structure must still be inlined.
            ObjectNode props = MAPPER.createObjectNode();
            props.set("spot", MAPPER.createObjectNode()
                    .<ObjectNode>put("$ref", GEO_URN)
                    .put("title", "Sampling spot"));
            ObjectNode schema = MAPPER.createObjectNode();
            schema.put("$id", "urn:core:platform:civitas:element:common:Site:yddl6euvis:1.0.0");
            schema.put("type", "object");
            schema.set("properties", props);
            when(mockRegistry.fetchElementOrXsd("urn:core:platform:civitas:element:common:Site:yddl6euvis:1.0.0"))
                    .thenReturn(Optional.of(schema));

            Optional<JsonNode> result =
                    viewService.inline("urn:core:platform:civitas:element:common:Site:yddl6euvis:1.0.0");

            assertThat(result).isPresent();
            JsonNode spot = result.get().path("properties").path("spot");
            assertThat(spot.path("title").asText())
                    .as("local title overrides the referenced schema's title")
                    .isEqualTo("Sampling spot");
            // still the inlined GeoPoint structure
            assertThat(spot.path("properties").has("lat")).isTrue();
            assertThat(spot.path("properties").has("lon")).isTrue();
        }

        @Test
        void deepChain_inlinesAllLevels() {
            // Observation → SensorReading → GeoPoint
            Optional<JsonNode> result = viewService.inline(OBS_URN);

            assertThat(result).isPresent();
            // "reading" in Observation should be inlined SensorReading
            JsonNode reading = result.get().path("properties").path("reading");
            assertThat(reading.path("title").asText()).isEqualTo("SensorReading");
            // Within the inlined SensorReading, "location" should be inlined GeoPoint
            JsonNode location = reading.path("properties").path("location");
            assertThat(location.path("title").asText()).isEqualTo("GeoPoint");
        }

        @Test
        void depthZero_doesNotInline() {
            Optional<JsonNode> result = viewService.inline(OBS_URN, 0);
            assertThat(result).isPresent();
            // reading stays a bare $ref — nothing inlined at depth 0
            assertThat(result.get().path("properties").path("reading").path("$ref").asText())
                    .isEqualTo(SENSOR_URN);
        }

        @Test
        void depthOne_inlinesOneHopOnly() {
            // Observation → SensorReading (1 hop) inlined; SensorReading → GeoPoint NOT inlined
            Optional<JsonNode> result = viewService.inline(OBS_URN, 1);
            assertThat(result).isPresent();
            JsonNode reading = result.get().path("properties").path("reading");
            assertThat(reading.path("title").asText()).isEqualTo("SensorReading"); // inlined
            assertThat(reading.path("properties").path("location").path("$ref").asText())
                    .isEqualTo(GEO_URN); // beyond depth → left as URN
        }

        @Test
        void cycle_leavesRefIntactAndDoesNotLoop() {
            // Create a cyclic schema: Alpha.$ref → Beta, Beta.$ref → Alpha
            String alphaUrn = "urn:core:platform:civitas:element:common:Alpha:xp1yy8mi4n:1.0.0";
            String betaUrn  = "urn:core:platform:civitas:element:common:Beta:4f0dvxin83:1.0.0";

            ObjectNode alphaSchema = MAPPER.createObjectNode();
            alphaSchema.put("$id", alphaUrn);
            alphaSchema.put("type", "object");
            ObjectNode alphaProps = MAPPER.createObjectNode();
            alphaProps.set("next", MAPPER.createObjectNode().put("$ref", betaUrn));
            alphaSchema.set("properties", alphaProps);

            ObjectNode betaSchema = MAPPER.createObjectNode();
            betaSchema.put("$id", betaUrn);
            betaSchema.put("type", "object");
            ObjectNode betaProps = MAPPER.createObjectNode();
            betaProps.set("back", MAPPER.createObjectNode().put("$ref", alphaUrn));
            betaSchema.set("properties", betaProps);

            when(mockRegistry.fetchElementOrXsd(alphaUrn)).thenReturn(Optional.of(alphaSchema));
            when(mockRegistry.fetchElementOrXsd(betaUrn)).thenReturn(Optional.of(betaSchema));

            // Must not throw StackOverflowError
            Optional<JsonNode> result = viewService.inline(alphaUrn);

            assertThat(result).isPresent();
            // "next" is the inlined betaSchema (first expansion)
            JsonNode inlinedBeta = result.get().path("properties").path("next");
            assertThat(inlinedBeta.has("$id")).isTrue();
            // "back" inside betaSchema is the inlined alphaSchema (second expansion)
            JsonNode inlinedAlpha = inlinedBeta.path("properties").path("back");
            assertThat(inlinedAlpha.has("$id")).isTrue();
            // Within that second alpha expansion, "next" must be a preserved $ref (cycle guard)
            JsonNode stoppedRef = inlinedAlpha.path("properties").path("next");
            assertThat(stoppedRef.path("$ref").asText("")).isEqualTo(betaUrn);
        }

        @Test
        void xsdAndJsonSchemaMixed_transparentlyResolved() {
            // An XSD URN whose fetch transparently returns a JSON Schema node
            String xsdUrn = "urn:core:platform:civitas:element:common:AirQuality:wyonmizavr:1.0.0";
            ObjectNode convertedXsd = MAPPER.createObjectNode();
            convertedXsd.put("$id", xsdUrn);
            convertedXsd.put("type", "object");
            convertedXsd.set("properties", MAPPER.createObjectNode()
                    .set("pm25", MAPPER.createObjectNode().put("type", "number")));

            ObjectNode jsonWithXsdRef = MAPPER.createObjectNode();
            jsonWithXsdRef.put("$id", SENSOR_URN);
            jsonWithXsdRef.put("type", "object");
            ObjectNode p = MAPPER.createObjectNode();
            p.set("airQuality", MAPPER.createObjectNode().put("$ref", xsdUrn));
            jsonWithXsdRef.set("properties", p);

            when(mockRegistry.fetchElementOrXsd(SENSOR_URN)).thenReturn(Optional.of(jsonWithXsdRef));
            when(mockRegistry.fetchElementOrXsd(xsdUrn)).thenReturn(Optional.of(convertedXsd));

            Optional<JsonNode> result = viewService.inline(SENSOR_URN);

            assertThat(result).isPresent();
            // The XSD ref should be inlined as a JSON Schema node
            JsonNode aq = result.get().path("properties").path("airQuality");
            assertThat(aq.path("$id").asText()).isEqualTo(xsdUrn);
            assertThat(aq.path("properties").has("pm25")).isTrue();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Bundle view
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    class BundleTests {

        @Test
        void registryAbsent_returnsEmpty() {
            ViewService noRegistry = new ViewService(mock(de.civitascore.modelforge.core.port.ArtifactRegistry.class), graph, MAPPER);
            assertThat(noRegistry.bundle(SENSOR_URN)).isEmpty();
        }

        @Test
        void unknownUrn_returnsEmpty() {
            when(mockRegistry.fetchElementOrXsd("urn:core:unknown")).thenReturn(Optional.empty());
            assertThat(viewService.bundle("urn:core:unknown")).isEmpty();
        }

        @Test
        void leafSchema_noDeps_noDefsSection() {
            // GeoPoint has no dependencies registered in the graph
            Optional<JsonNode> result = viewService.bundle(GEO_URN);

            assertThat(result).isPresent();
            assertThat(result.get().has("$defs")).isFalse();
        }

        @Test
        void schemaWithOneDep_defsContainsDep() {
            // Register SensorReading → GeoPoint
            graph.register(SENSOR_URN, Set.of(GEO_URN));

            Optional<JsonNode> result = viewService.bundle(SENSOR_URN);

            assertThat(result).isPresent();
            assertThat(result.get().path("$defs").has("GeoPoint")).isTrue();
        }

        @Test
        void crossDocRefLeftAbsolute_andDepEmbeddedWithId() {
            graph.register(SENSOR_URN, Set.of(GEO_URN));

            Optional<JsonNode> result = viewService.bundle(SENSOR_URN);

            assertThat(result).isPresent();
            // The cross-document ref is left as the absolute CORE URN (NOT rewritten to
            // #/$defs/GeoPoint) so it resolves against the embedded resource's $id.
            String locationRef = result.get()
                    .path("properties").path("location").path("$ref").asText();
            assertThat(locationRef).isEqualTo(GEO_URN);
            // The dependency is embedded as a resource that still carries its own $id.
            assertThat(result.get().path("$defs").path("GeoPoint").path("$id").asText())
                    .isEqualTo(GEO_URN);
        }

        @Test
        void transitiveChain_allDepsInDefs() {
            // Observation → SensorReading → GeoPoint (2-hop chain)
            graph.register(OBS_URN, Set.of(SENSOR_URN));
            graph.register(SENSOR_URN, Set.of(GEO_URN));

            Optional<JsonNode> result = viewService.bundle(OBS_URN);

            assertThat(result).isPresent();
            JsonNode defs = result.get().path("$defs");
            // Both transitive deps should appear in $defs
            assertThat(defs.has("SensorReading")).isTrue();
            assertThat(defs.has("GeoPoint")).isTrue();
        }

        @Test
        void schemaAndIdPreserved() {
            Optional<JsonNode> result = viewService.bundle(SENSOR_URN);

            assertThat(result).isPresent();
            assertThat(result.get().path("$schema").asText())
                    .isEqualTo("https://json-schema.org/draft/2020-12/schema");
            assertThat(result.get().path("$id").asText()).isEqualTo(SENSOR_URN);
        }

        @Test
        void localDefsFromRootSchemaArePreserved() {
            // Bug guard: bundle() previously dropped any $defs that were already in the root
            // schema (local inline types, not CORE registry deps). After the fix, local $defs
            // must survive and be merged with the CORE-dep $defs section.
            ObjectNode localDef = MAPPER.createObjectNode();
            localDef.put("type", "string");
            localDef.put("description", "Local status enum");

            ObjectNode schemaWithLocalDefs = MAPPER.createObjectNode();
            schemaWithLocalDefs.put("$id", SENSOR_URN);
            schemaWithLocalDefs.put("type", "object");
            ObjectNode defs = MAPPER.createObjectNode();
            defs.set("Status", localDef);
            schemaWithLocalDefs.set("$defs", defs);
            ObjectNode props = MAPPER.createObjectNode();
            props.set("status", MAPPER.createObjectNode().put("$ref", "#/$defs/Status"));
            schemaWithLocalDefs.set("properties", props);

            when(mockRegistry.fetchElementOrXsd(SENSOR_URN)).thenReturn(Optional.of(schemaWithLocalDefs));

            Optional<JsonNode> result = viewService.bundle(SENSOR_URN);

            assertThat(result).isPresent();
            assertThat(result.get().path("$defs").has("Status")).isTrue();
            assertThat(result.get().path("$defs").path("Status").path("description").asText())
                    .isEqualTo("Local status enum");
        }

        @Test
        void nameCollision_keepsLocalDef_andEmbedsCoreDepUnderDistinctKey() {
            // Root schema has a local $defs.GeoPoint (a string placeholder) that its own
            // internal "#/$defs/GeoPoint" pointers depend on. The graph also registers a
            // CORE GeoPoint dependency. The local key MUST be preserved (otherwise the
            // root's internal pointers would silently change meaning), and the CORE dep
            // must still be embedded — under a distinct, collision-free key — so it is
            // not dropped and its $id remains resolvable.
            ObjectNode localGeoPoint = MAPPER.createObjectNode();
            localGeoPoint.put("type", "string");
            localGeoPoint.put("description", "Local placeholder — must be preserved");

            ObjectNode schemaWithLocalGeoPoint = MAPPER.createObjectNode();
            schemaWithLocalGeoPoint.put("$id", SENSOR_URN);
            schemaWithLocalGeoPoint.put("type", "object");
            ObjectNode localDefs = MAPPER.createObjectNode();
            localDefs.set("GeoPoint", localGeoPoint);
            schemaWithLocalGeoPoint.set("$defs", localDefs);

            when(mockRegistry.fetchElementOrXsd(SENSOR_URN))
                    .thenReturn(Optional.of(schemaWithLocalGeoPoint));
            // GEO_URN / geoL is already stubbed in setUp() to return GEO_SCHEMA (object with lat/lon)

            graph.register(SENSOR_URN, Set.of(GEO_URN));

            Optional<JsonNode> result = viewService.bundle(SENSOR_URN);

            assertThat(result).isPresent();
            JsonNode defs = result.get().path("$defs");

            // Local def preserved under its original key
            assertThat(defs.path("GeoPoint").path("type").asText())
                    .as("local placeholder must keep its key")
                    .isEqualTo("string");

            // CORE dep embedded under the collision-free fallback key (the logical URN),
            // carrying its own $id so absolute refs to it still resolve.
            String logicalGeo = "urn:core:platform:civitas:element:common:GeoPoint:3ak90vqrog";
            JsonNode coreGeo = defs.path(logicalGeo);
            assertThat(coreGeo.path("$id").asText()).isEqualTo(GEO_URN);
            assertThat(coreGeo.path("type").asText()).isEqualTo("object");
            assertThat(coreGeo.path("properties").has("lat")).isTrue();
            assertThat(coreGeo.path("properties").has("lon")).isTrue();
        }

        // ── Self-containment: every cross-document ref resolves to an embedded $id ──
        //
        // This is the resolution contract the bundle must satisfy: a 2020-12 validator
        // resolves an absolute CORE-URN $ref by finding an embedded resource whose $id
        // equals that URN. So for the bundle to be self-contained, the set of CORE-URN
        // $ref values must be a subset of the set of $id values present in the document.
        // (Verified end-to-end against a real Draft 2020-12 validator in the integration
        // run; here we assert the structural invariant deterministically.)

        private void assertSelfContained(JsonNode bundle) {
            Set<String> ids  = new java.util.HashSet<>();
            Set<String> refs = new java.util.HashSet<>();
            collectIds(bundle, ids);
            collectCoreUrnRefs(bundle, refs);
            assertThat(refs)
                    .as("every cross-document URN $ref must resolve to an embedded $id; "
                        + "ids=%s refs=%s", ids, refs)
                    .isSubsetOf(ids);
        }

        @Test
        void chain_isSelfContained() {
            // Observation → SensorReading → GeoPoint (dep→dep edge)
            graph.register(OBS_URN, Set.of(SENSOR_URN));
            graph.register(SENSOR_URN, Set.of(GEO_URN));

            Optional<JsonNode> result = viewService.bundle(OBS_URN);

            assertThat(result).isPresent();
            assertSelfContained(result.get());
            // both transitive deps embedded as resources with their $ids
            assertThat(result.get().path("$defs").path("SensorReading").path("$id").asText())
                    .isEqualTo(SENSOR_URN);
            assertThat(result.get().path("$defs").path("GeoPoint").path("$id").asText())
                    .isEqualTo(GEO_URN);
        }

        @Test
        void depthLimitsEmbeddedDeps() {
            // Observation → SensorReading → GeoPoint
            graph.register(OBS_URN, Set.of(SENSOR_URN));
            graph.register(SENSOR_URN, Set.of(GEO_URN));

            // depth 1: only the direct dependency is embedded
            JsonNode d1 = viewService.bundle(OBS_URN, 1).orElseThrow();
            assertThat(d1.path("$defs").has("SensorReading")).isTrue();
            assertThat(d1.path("$defs").has("GeoPoint")).isFalse();

            // depth 2: the second hop is embedded too
            JsonNode d2 = viewService.bundle(OBS_URN, 2).orElseThrow();
            assertThat(d2.path("$defs").has("SensorReading")).isTrue();
            assertThat(d2.path("$defs").has("GeoPoint")).isTrue();

            // depth 0: nothing embedded
            JsonNode d0 = viewService.bundle(OBS_URN, 0).orElseThrow();
            assertThat(d0.has("$defs")).isFalse();
        }

        @Test
        void diamond_isSelfContained() {
            // Root → Left, Root → Right, Left → Leaf, Right → Leaf
            String rootU  = "urn:core:platform:civitas:element:common:DiaRoot:p0qrt4g4of:1.0.0";
            String leftU  = "urn:core:platform:civitas:element:common:DiaLeft:wt5tlk2msa:1.0.0";
            String rightU = "urn:core:platform:civitas:element:common:DiaRight:0no7xq907w:1.0.0";
            String leafU  = "urn:core:platform:civitas:element:common:DiaLeaf:tu4klmd7l1:1.0.0";

            stubObj(rootU, props -> {
                props.set("l", MAPPER.createObjectNode().put("$ref", leftU));
                props.set("r", MAPPER.createObjectNode().put("$ref", rightU));
            });
            stubObj(leftU,  props -> props.set("leaf", MAPPER.createObjectNode().put("$ref", leafU)));
            stubObj(rightU, props -> props.set("leaf", MAPPER.createObjectNode().put("$ref", leafU)));
            stubObj(leafU,  props -> props.set("v", MAPPER.createObjectNode().put("type", "string")));

            graph.register(rootU,  Set.of(leftU, rightU));
            graph.register(leftU,  Set.of(leafU));
            graph.register(rightU, Set.of(leafU));

            Optional<JsonNode> result = viewService.bundle(rootU);

            assertThat(result).isPresent();
            assertSelfContained(result.get());
        }

        @Test
        void cycle_bundleTerminatesAndIsSelfContained() {
            // Alpha → Beta → Alpha
            String alphaU = "urn:core:platform:civitas:element:common:Alpha:xp1yy8mi4n:1.0.0";
            String betaU  = "urn:core:platform:civitas:element:common:Beta:4f0dvxin83:1.0.0";

            stubObj(alphaU, props -> props.set("next", MAPPER.createObjectNode().put("$ref", betaU)));
            stubObj(betaU,  props -> props.set("back", MAPPER.createObjectNode().put("$ref", alphaU)));

            graph.register(alphaU, Set.of(betaU));
            graph.register(betaU,  Set.of(alphaU));

            Optional<JsonNode> result = viewService.bundle(alphaU);

            assertThat(result).isPresent();
            assertSelfContained(result.get());
            // Beta embedded as a resource; the root (Alpha) provides the Alpha $id itself.
            assertThat(result.get().path("$id").asText()).isEqualTo(alphaU);
            assertThat(result.get().path("$defs").path("Beta").path("$id").asText()).isEqualTo(betaU);
        }

        @Test
        void depToDepRefLeftAbsolute_resolvesViaEmbeddedId() {
            // Bug guard for the dep→dep case: in a bundle of Observation, the embedded
            // SensorReading references GeoPoint. That ref MUST stay the absolute URN.
            // Rewriting it to "#/$defs/GeoPoint" would be unresolvable, because inside the
            // embedded SensorReading resource (own $id) the pointer rebases to itself.
            graph.register(OBS_URN, Set.of(SENSOR_URN));
            graph.register(SENSOR_URN, Set.of(GEO_URN));

            Optional<JsonNode> result = viewService.bundle(OBS_URN);

            assertThat(result).isPresent();
            String locationRef = result.get()
                    .path("$defs").path("SensorReading")
                    .path("properties").path("location").path("$ref").asText();
            assertThat(locationRef).isEqualTo(GEO_URN);
            // GeoPoint must be present as an embedded resource carrying its $id so the
            // absolute ref above resolves.
            assertThat(result.get().path("$defs").path("GeoPoint").path("$id").asText())
                    .isEqualTo(GEO_URN);
        }

        @Test
        void xsdConvertedDep_withTypeLevelId_isSelfContained() {
            // DSVIEW-1 guard: an XSD-authored Element converts to a JSON Schema whose
            // $id is TYPE-LEVEL — one segment longer than the model_forge.artifact URN (the XÖV/XMeld
            // case, e.g. ...:Person:Personentyp:1.0.0). The root pins the *model_forge.artifact* URN in
            // its $ref, so the embedded resource's $id must be stamped to the model_forge.artifact URN;
            // otherwise the $ref dangles and the bundle is not self-contained.
            String xsdDepUrn   = "urn:core:platform:civitas:element:xmeld:Person:o73r9yb879:1.0.0";
            // Converted schema's $id is type-level (model_forge.artifact URN + ":Personentyp:1.0.0"-style
            // extra segment) — deliberately NOT equal to the model_forge.artifact URN, so the old guard
            // (existing == null || !isUrn(existing)) would have kept it and dangled the $ref.
            String typeLevelId = "urn:core:platform:civitas:element:xmeld:Person:o73r9yb879:Personentyp:1.0.0";

            ObjectNode convertedXsd = MAPPER.createObjectNode();
            convertedXsd.put("$id", typeLevelId);
            convertedXsd.put("type", "object");
            convertedXsd.set("properties", MAPPER.createObjectNode()
                    .set("name", MAPPER.createObjectNode().put("type", "string")));

            ObjectNode rootSchema = MAPPER.createObjectNode();
            rootSchema.put("$id", OBS_URN);
            rootSchema.put("type", "object");
            rootSchema.set("properties", MAPPER.createObjectNode()
                    .set("person", MAPPER.createObjectNode().put("$ref", xsdDepUrn)));

            when(mockRegistry.fetchElementOrXsd(OBS_URN)).thenReturn(Optional.of(rootSchema));
            when(mockRegistry.fetchElementOrXsd(xsdDepUrn)).thenReturn(Optional.of(convertedXsd));
            when(mockRegistry.fetchElementOrXsd(logical(xsdDepUrn)))
                    .thenReturn(Optional.of(convertedXsd));

            graph.register(OBS_URN, Set.of(xsdDepUrn));

            Optional<JsonNode> result = viewService.bundle(OBS_URN);

            assertThat(result).isPresent();
            // The whole bundle must be self-contained: every CORE-URN $ref resolves to an $id.
            assertSelfContained(result.get());
            // Concretely: the root's $ref to the model_forge.artifact URN resolves to an embedded resource
            // whose $id was stamped to that exact model_forge.artifact URN (NOT the type-level $id).
            String personRef = result.get()
                    .path("properties").path("person").path("$ref").asText();
            assertThat(personRef).isEqualTo(xsdDepUrn);
            JsonNode embedded = result.get().path("$defs").path("Person");
            assertThat(embedded.path("$id").asText())
                    .as("embedded XSD-converted dep $id must be stamped to the model_forge.artifact URN, "
                        + "not kept at the type-level $id")
                    .isEqualTo(xsdDepUrn);
            // The converted body (its properties) is preserved alongside the rewritten $id.
            assertThat(embedded.path("properties").has("name")).isTrue();
        }
    }

}
