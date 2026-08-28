package de.civitascore.modelforge.application;

import tools.jackson.databind.JsonNode;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.contract.ArtifactSummary;
import de.civitascore.modelforge.contract.ArtifactKind;
import de.civitascore.modelforge.contract.ArtifactView;
import de.civitascore.modelforge.contract.ArtifactWriteResult;
import de.civitascore.modelforge.contract.BumpVersionCommand;
import de.civitascore.modelforge.contract.CreateArtifactCommand;
import de.civitascore.modelforge.contract.DependencyGraphView;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.DiagnosticSeverity;
import de.civitascore.modelforge.contract.ImportResult;
import de.civitascore.modelforge.contract.ImportSchemaCommand;
import de.civitascore.modelforge.contract.ImportSmartDataModelCommand;
import de.civitascore.modelforge.contract.ImportXRepositoryCommand;
import de.civitascore.modelforge.contract.SaveArtifactCommand;
import de.civitascore.modelforge.contract.SchemaViewQuery;
import de.civitascore.modelforge.contract.ValidateInstanceCommand;
import de.civitascore.modelforge.contract.ValidateSchemaCommand;
import de.civitascore.modelforge.contract.ValidationFailedException;
import de.civitascore.modelforge.contract.ValidationResult;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.contract.XRepositoryHit;
import de.civitascore.modelforge.contract.XRepositorySearchQuery;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.core.port.ArtifactSearchCriteria;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.urn.UrnService;
import de.civitascore.modelforge.validation.CoreSchemaValidator;
import de.civitascore.modelforge.validation.ModelValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class EmbeddedModelForgeOperations implements ModelForge {

    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger(EmbeddedModelForgeOperations.class);

    private final SchemaImportService schemaImportService;
    private final ElementQueryService elementQueryService;
    private final ElementCommandService elementCommandService;
    private final ViewService viewService;
    private final ModelValidator modelValidator;
    private final CoreSchemaValidator coreSchemaValidator;
    private final ReferenceExistenceValidator referenceExistenceValidator;
    private final DependencyGraphService dependencyGraph;
    private final ArtifactRegistry registry;
    private final SmartDataModelsService smartDataModelsService;
    private final XRepositoryService xRepositoryService;
    private final UrnService urns;

    public EmbeddedModelForgeOperations(
        SchemaImportService schemaImportService,
        ElementQueryService elementQueryService,
        ElementCommandService elementCommandService,
        ViewService viewService,
        ModelValidator modelValidator,
        CoreSchemaValidator coreSchemaValidator,
        ReferenceExistenceValidator referenceExistenceValidator,
        DependencyGraphService dependencyGraph,
        ArtifactRegistry registry,
        SmartDataModelsService smartDataModelsService,
        XRepositoryService xRepositoryService,
        UrnService urns
    ) {
        this.schemaImportService = schemaImportService;
        this.elementQueryService = elementQueryService;
        this.elementCommandService = elementCommandService;
        this.viewService = viewService;
        this.modelValidator = modelValidator;
        this.coreSchemaValidator = coreSchemaValidator;
        this.referenceExistenceValidator = referenceExistenceValidator;
        this.dependencyGraph = dependencyGraph;
        this.registry = registry;
        this.smartDataModelsService = smartDataModelsService;
        this.xRepositoryService = xRepositoryService;
        this.urns = urns;
    }

    @Override
    public ImportResult importSchema(ImportSchemaCommand command) {
        var response = schemaImportService.importSchema(new SchemaImportRequest(
            command.schema(), command.version(), command.preserveVersion(), command.bump()));
        return toImportResult(response, "Schema import validation failed");
    }

    @Override
    public ImportResult importFromSmartDataModels(ImportSmartDataModelCommand command) {
        var response = smartDataModelsService.importModel(command.subject(), command.dataModel());
        return toImportResult(response, "Smart Data Model import validation failed");
    }

    @Override
    public List<XRepositoryHit> searchXRepository(XRepositorySearchQuery query) {
        var page = xRepositoryService.search(query.query(), query.page(), query.size());
        return page.items().stream()
            .map(hit -> new XRepositoryHit(
                hit.identifier(),
                hit.parentIdentifier(),
                hit.name(),
                hit.version(),
                hit.description(),
                hit.kind(),
                hit.status()
            ))
            .toList();
    }

    @Override
    public ImportResult importFromXRepository(ImportXRepositoryCommand command) {
        var response = xRepositoryService.importArtifact(new XRepositoryImportCommand(
            command.identifier(),
            command.version(),
            command.scope(),
            command.owner(),
            command.importAsXsd(),
            command.preserveUpstreamVersion()
        ));
        return toImportResult(response, "XRepository import validation failed");
    }

    /**
     * Maps a {@link SchemaImportResult} to the facade's {@link ImportResult}, shared by every
     * import entry point ({@link #importSchema}, {@link #importFromSmartDataModels},
     * {@link #importFromXRepository}) — same failure handling, same
     * root-plus-every-created-Element shape.
     *
     * @throws ValidationFailedException when the import reported any error diagnostic
     */
    private ImportResult toImportResult(SchemaImportResult response, String failureMessage) {
        if (hasErrors(response.diagnostics())) {
            throw new ValidationFailedException(failureMessage, response.diagnostics());
        }
        var root = pinFromResult(response.resourceId());
        var imported = response.importedResourceIds().stream().map(this::pinFromResult).toList();
        return new ImportResult(root, imported, dependenciesOf(root));
    }

    @Override
    public Optional<ArtifactView> getArtifact(ArtifactId artifactId) {
        return elementQueryService.rawJsonSchema(artifactId.value())
            .map(content -> new ArtifactView(artifactId, content));
    }

    @Override
    public Optional<ArtifactView> getBundledView(SchemaViewQuery query) {
        var content = query.maxDepth() != null
            ? viewService.bundle(query.artifactId().value(), query.maxDepth())
            : viewService.bundle(query.artifactId().value());
        return content.map(c -> new ArtifactView(query.artifactId(), c));
    }

    @Override
    public Optional<ArtifactView> getInlinedView(SchemaViewQuery query) {
        var content = query.maxDepth() != null
            ? viewService.inline(query.artifactId().value(), query.maxDepth())
            : viewService.inline(query.artifactId().value());
        return content.map(c -> new ArtifactView(query.artifactId(), c));
    }

    @Override
    public ValidationResult validateSchema(ValidateSchemaCommand command) {
        return validationResult(modelValidator.validateSchema(command.schema()));
    }

    @Override
    public ValidationResult validateInstance(ValidateInstanceCommand command) {
        return validationResult(modelValidator.validateData(command.schema(), command.instance()));
    }

    @Override
    public DependencyGraphView dependencies(DependencyQuery query) {
        var source = query.artifactId();
        var targetUrns = query.maxDepth() != null
            ? dependencyGraph.getTransitiveDependencies(source.value(), query.maxDepth())
            : dependencyGraph.getDependencies(source.value());
        var targets = targetUrns.stream().map(ArtifactId::new).toList();
        var nodes = new java.util.ArrayList<DependencyGraphView.Node>();
        nodes.add(new DependencyGraphView.Node(source, UrnParser.nameFromUrn(source.value())));
        targets.stream()
            .map(target -> new DependencyGraphView.Node(target, UrnParser.nameFromUrn(target.value())))
            .forEach(nodes::add);
        var edges = targets.stream()
            .map(target -> new DependencyGraphView.Edge(source, target, "depends-on"))
            .toList();
        return new DependencyGraphView(nodes, edges);
    }

    @Override
    public DependencyGraphView dependents(DependencyQuery query) {
        var source = query.artifactId();
        var originUrns = query.maxDepth() != null
            ? dependencyGraph.getTransitiveDependents(source.value(), query.maxDepth())
            : dependencyGraph.getDependents(source.value());
        var origins = originUrns.stream().map(ArtifactId::new).toList();
        var nodes = new ArrayList<DependencyGraphView.Node>();
        nodes.add(new DependencyGraphView.Node(source, UrnParser.nameFromUrn(source.value())));
        origins.stream()
            .map(origin -> new DependencyGraphView.Node(origin, UrnParser.nameFromUrn(origin.value())))
            .forEach(nodes::add);
        var edges = origins.stream()
            .map(origin -> new DependencyGraphView.Edge(origin, source, "depends-on"))
            .toList();
        return new DependencyGraphView(nodes, edges);
    }

    @Override
    public DependencyGraphView mapsTo(DependencyQuery query) {
        return mappingEdges(query.artifactId(), "source", "target", "maps-to");
    }

    @Override
    public DependencyGraphView mappedFrom(DependencyQuery query) {
        return mappingEdges(query.artifactId(), "target", "source", "mapped-from");
    }

    /**
     * Builds a small two-hop graph around {@code artifactId}'s mapping relations: an edge to each
     * mapping that references it in the given {@code role}, and — when the mapping also names the
     * other side — an edge from that mapping to the other artifact, so the visible graph shows the
     * actual data flow ({@code Element -> Mapping -> Element}) rather than just "used in a mapping".
     */
    private DependencyGraphView mappingEdges(ArtifactId artifactId, String role, String otherKey, String mappingRelation) {
        List<Map<String, String>> rows = registry.mappingsForElement(artifactId.value(), role);
        var nodes = new ArrayList<DependencyGraphView.Node>();
        var edges = new ArrayList<DependencyGraphView.Edge>();
        nodes.add(new DependencyGraphView.Node(artifactId, UrnParser.nameFromUrn(artifactId.value())));
        for (Map<String, String> row : rows) {
            String mappingUrn = row.get("mapping");
            if (mappingUrn == null) continue;
            var mappingId = new ArtifactId(mappingUrn);
            nodes.add(new DependencyGraphView.Node(mappingId, UrnParser.nameFromUrn(mappingUrn)));
            edges.add("source".equals(role)
                ? new DependencyGraphView.Edge(artifactId, mappingId, "source-of")
                : new DependencyGraphView.Edge(mappingId, artifactId, "target-of"));
            String otherUrn = row.get(otherKey);
            if (otherUrn != null) {
                var otherId = new ArtifactId(otherUrn);
                nodes.add(new DependencyGraphView.Node(otherId, UrnParser.nameFromUrn(otherUrn)));
                edges.add("source".equals(role)
                    ? new DependencyGraphView.Edge(mappingId, otherId, mappingRelation)
                    : new DependencyGraphView.Edge(otherId, mappingId, mappingRelation));
            }
        }
        return new DependencyGraphView(nodes, edges);
    }

    @Override
    public List<ArtifactSummary> search(ArtifactSearchQuery query) {
        var criteria = new ArtifactSearchCriteria(
            query.text(),
            false,
            query.type(),
            query.format(),
            null,
            null,
            query.limit() > 0 ? query.limit() : 50,
            Math.max(query.offset(), 0)
        );
        return registry.searchArtifacts(criteria).stream()
            .map(hit -> new ArtifactSummary(
                new ArtifactId(hit.id()),
                                hit.type(),
                hit.title(),
                hit.version(),
                hit.format()
            ))
            .toList();
    }

    @Override
    public ArtifactWriteResult createArtifact(CreateArtifactCommand command) {
        String urn = switch (command.kind()) {
            case MAPPING -> urns.mintMapping(command.name());
            case PIPELINE -> urns.mintPipeline(command.name());
            case DATA_SOURCE -> urns.mintDataSource(command.name());
            case DATA_SINK -> urns.mintDataSink(command.name());
            case DATA_SET -> urns.mintDataSet(command.name());
            case DATA_STRUCTURE -> urns.mintDataStructure(command.name());
            case ELEMENT -> throw new IllegalArgumentException(
                "Elements are created through importSchema, which mints the URN and splits $defs "
                + "into their own Elements.");
        };
        // Model Forge owns a created artifact's identity: the document's id always carries the
        // minted URN, regardless of any id the caller left in the content.
        var content = command.content();
        if (content.isObject()) {
            tools.jackson.databind.node.ObjectNode stamped =
                (tools.jackson.databind.node.ObjectNode) content.deepCopy();
            stamped.put("id", urn);
            content = stamped;
        }
        return saveArtifact(
            new SaveArtifactCommand(
                new ArtifactId(urn), command.kind(), content, VersionBump.PATCH, command.dataSet()));
    }

    @Override
    public ArtifactWriteResult saveArtifact(SaveArtifactCommand command) {
        String urn = command.artifactId().value();
        // Stamp the CORE $schema for the opaque payload kinds (Mapping/Pipeline/DataSource/DataSink)
        // so the stored document self-describes and satisfies its schema's required:["$schema"].
        // DataStructure/DataSet keep the JSON-Schema meta-schema $schema they already carry; Element
        // is never stamped. All non-Element writes flow through here, so this is the single choke point.
        JsonNode content = command.content();
        String schemaUri = CoreSchemaValidator.schemaUriToStamp(command.kind());
        if (schemaUri != null && content != null && content.isObject()) {
            // Model Forge owns identity + self-description: stamp $schema AND id on EVERY write of an
            // opaque payload kind (create and re-version), so the host never stamps either. (Create
            // also stamps id in createArtifact; re-version relies on this.)
            tools.jackson.databind.node.ObjectNode stamped =
                (tools.jackson.databind.node.ObjectNode) content.deepCopy();
            stamped.put("$schema", schemaUri);
            stamped.put("id", urn);
            content = stamped;
        }
        // Mandatory structural validation: every artifact must satisfy the CORE schema for its kind
        // (Element excepted — it is an arbitrary JSON Schema validated on import). No more opt-in.
        List<Diagnostic> violations = coreSchemaValidator.validate(command.kind(), content);
        if (violations.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR)) {
            LOG.warn("Artifact {} ({}) rejected by its CORE schema: {}", urn, command.kind(), violations);
            throw new ValidationFailedException(
                "Artifact does not satisfy its CORE schema", violations);
        }
        // x-core-ref foreign keys are existence-checked on every write, not just on import: a save
        // may introduce a new reference, so the target must resolve now too. The check keys off the
        // content (it only does work when the document actually carries x-core-ref), so it needs no
        // artifact-kind branch; a lone save has no co-imported corpus beyond the artifact itself.
        List<Diagnostic> unresolvedRefs = referenceExistenceValidator.checkForeignKeys(
            content, Set.of(UrnParser.logicalUrn(urn)));
        if (!unresolvedRefs.isEmpty()) {
            throw new ValidationFailedException(
                "Artifact has unresolved x-core-ref foreign keys", unresolvedRefs);
        }
        var bump = command.versionBump() == null ? VersionBump.PATCH : command.versionBump();
        // Every write returns the concrete versioned URN (pin) the registry assigned INSIDE the
        // write itself — no read-back, so the pin is correct even when the write runs inside a
        // surrounding, not-yet-committed host transaction.
        String assigned = switch (command.kind()) {
            // An Element is stored as JSON Schema or as XSD depending on its representation format,
            // recognised from the content: a textual node carries a raw XSD document, a JSON object
            // is a JSON Schema. Both share the format-agnostic :element: URN and register their graph
            // edges inside ElementCommandService. storeJsonSchema() only backfills $id when the
            // content has none, so content round-tripped from a fetched (pinned-version) artifact
            // would keep its stale versioned $id and silently defeat the version bump; the caller's
            // urn is the authoritative target identity, so always re-point $id at it (withoutId).
            case ELEMENT -> content.isTextual()
                ? elementCommandService.storeXsd(urn, content.asText(), bump)
                : elementCommandService.storeJsonSchema(urn, withoutId(content), bump);
            // The non-Element kinds store straight through the registry, which extracts and
            // persists their per-type reference edges (Mapping source/target, Pipeline nodes,
            // DataStructure/DataSet *Refs). registerFromRegistry() then mirrors those durable
            // edges into the in-memory graph so dependencies()/dependents() see them.
            case DATA_STRUCTURE -> { String p = registry.storeDataStructure(urn, content, bump); dependencyGraph.registerFromRegistry(urn); yield p; }
            case MAPPING -> { String p = registry.storeMapping(urn, content, bump); dependencyGraph.registerFromRegistry(urn); yield p; }
            case PIPELINE -> { String p = registry.storePipeline(urn, content, bump); dependencyGraph.registerFromRegistry(urn); yield p; }
            case DATA_SOURCE -> { String p = registry.storeDataSource(urn, content, bump); dependencyGraph.registerFromRegistry(urn); yield p; }
            case DATA_SINK -> { String p = registry.storeDataSink(urn, content, bump); dependencyGraph.registerFromRegistry(urn); yield p; }
            case DATA_SET -> { String p = registry.storeDataSet(urn, content, bump); dependencyGraph.registerFromRegistry(urn); yield p; }
        };
        // Defensive fallback (a registry double may return nothing): the honest unversioned
        // logical URN — deliberately NOT a resolveReference read-back.
        var pin = new ArtifactId(assigned != null && !assigned.isBlank()
            ? assigned : UrnParser.logicalUrn(urn));
        // DataSet membership: when the write requested it, link this artifact into the DataSet's
        // manifest (dataset-ref edges). A Pipeline drags its whole reference closure in too — the
        // sources/sinks/mappings it uses and, transitively, their DataStructures. A DataSet manifest
        // is never itself linked into a DataSet.
        if (command.dataSet() != null && command.kind() != ArtifactKind.DATA_SET) {
            java.util.Map<String, ArtifactKind> members = new java.util.LinkedHashMap<>();
            members.put(pin.value(), command.kind());
            if (command.kind() == ArtifactKind.PIPELINE) {
                collectMemberDeps(pin.value(), members);
            }
            linkMembersIntoDataSet(command.dataSet(), members);
        }
        return new ArtifactWriteResult(pin, dependenciesOf(pin));
    }

    /**
     * Recursively collects the DataSet-member artifacts that {@code urn} references (via the stored
     * reference edges), classifying each by its CORE artifact type. Used to drag a Pipeline's whole
     * reference closure (sources/sinks/mappings and, transitively, their DataStructures) into a
     * DataSet. Non-member kinds (e.g. Element) are skipped.
     */
    private void collectMemberDeps(String urn, java.util.Map<String, ArtifactKind> out) {
        for (String dep : registry.fetchArtifactRefUrns(urn)) {
            ArtifactKind kind = memberKindOf(dep);
            if (kind == null || out.containsKey(dep)) {
                continue;
            }
            out.put(dep, kind);
            collectMemberDeps(dep, out);
        }
    }

    /** The DataSet-member ArtifactKind for a CORE URN, or {@code null} for a non-member kind. */
    private static ArtifactKind memberKindOf(String urn) {
        return switch (UrnParser.artifactTypeFromUrn(urn) == null ? "" : UrnParser.artifactTypeFromUrn(urn)) {
            case "pipeline" -> ArtifactKind.PIPELINE;
            case "mapping" -> ArtifactKind.MAPPING;
            case "datasource" -> ArtifactKind.DATA_SOURCE;
            case "datasink" -> ArtifactKind.DATA_SINK;
            case "datastructure" -> ArtifactKind.DATA_STRUCTURE;
            default -> null;
        };
    }

    /** The DataSet manifest {@code *Refs} field for a member kind, or {@code null} if not a member. */
    private static String refFieldFor(ArtifactKind kind) {
        return switch (kind) {
            case PIPELINE -> "pipelineRefs";
            case DATA_SOURCE -> "dataSourceRefs";
            case DATA_SINK -> "dataSinkRefs";
            case MAPPING -> "mappingRefs";
            case DATA_STRUCTURE -> "datastructureRefs";
            case DATA_SET, ELEMENT -> null;
        };
    }

    /**
     * Adds each member (by its logical URN, in the {@code *Refs} array matching its kind) to
     * {@code dataSetUrn}'s manifest and re-stores it once — the {@code dataset-ref} edges that make
     * them DataSet members. Members already listed are skipped; the manifest is re-stored only when it
     * actually changed (so re-saving an existing member mints no new manifest version). A no-op
     * (logged) when the DataSet manifest does not exist.
     */
    private void linkMembersIntoDataSet(String dataSetUrn, java.util.Map<String, ArtifactKind> members) {
        Optional<ArtifactView> manifest = getArtifact(new ArtifactId(dataSetUrn));
        if (manifest.isEmpty() || manifest.get().content() == null || !manifest.get().content().isObject()) {
            LOG.warn("DataSet {} not found (or not an object); cannot link members {}", dataSetUrn, members.keySet());
            return;
        }
        tools.jackson.databind.node.ObjectNode doc =
            (tools.jackson.databind.node.ObjectNode) manifest.get().content().deepCopy();
        boolean changed = false;
        for (var entry : members.entrySet()) {
            String field = refFieldFor(entry.getValue());
            if (field == null) {
                continue;
            }
            String memberLogical = UrnParser.logicalUrn(entry.getKey());
            tools.jackson.databind.node.ArrayNode refs =
                doc.get(field) instanceof tools.jackson.databind.node.ArrayNode a ? a : doc.putArray(field);
            boolean present = false;
            for (JsonNode existing : refs) {
                if (memberLogical.equals(UrnParser.logicalUrn(existing.asText()))) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                refs.add(memberLogical);
                changed = true;
            }
        }
        if (changed) {
            saveArtifact(new SaveArtifactCommand(
                new ArtifactId(UrnParser.logicalUrn(dataSetUrn)), ArtifactKind.DATA_SET, doc, VersionBump.MINOR));
        }
    }

    /**
     * The pin for an import result: when the service carried the registry-assigned versioned URN
     * through (the write itself returned it), use it verbatim. Only a version-less resource id —
     * e.g. from a registry double that returned no pin — falls back to {@link #pinnedId}.
     */
    private ArtifactId pinFromResult(String resourceId) {
        String version = resourceId == null ? null : UrnParser.versionFromUrn(resourceId);
        if (version != null && !UrnParser.LATEST.equals(version)) {
            return new ArtifactId(resourceId);
        }
        return pinnedId(resourceId);
    }

    /**
     * Fallback pin resolution for results that carry no registry-assigned versioned URN: reads
     * the current version back via {@link ArtifactRegistry#resolveReference}. Note this read-back
     * may not see a write made in a surrounding, not-yet-committed host transaction — which is
     * exactly why the write paths return their pin directly and this is a fallback only.
     * Falls back to the logical URN when resolution yields nothing.
     */
    private ArtifactId pinnedId(String urnHint) {
        String logical = UrnParser.logicalUrn(urnHint);
        return new ArtifactId(registry.resolveReference(logical).orElse(logical));
    }

    /**
     * The written version's outgoing reference edges, grouped by stored reference type in
     * document order — the HAL-link-style dependency lists a host mirrors into its own
     * persistence. Read back from the registry after the write, so the lists reflect exactly
     * what the reference extraction persisted for this version.
     */
    private Map<String, List<ArtifactId>> dependenciesOf(ArtifactId pin) {
        Map<String, List<ArtifactId>> byType = new java.util.LinkedHashMap<>();
        registry.referencesByType(pin.value()).forEach((rel, targets) ->
            byType.put(rel, targets.stream().map(ArtifactId::new).toList()));
        return byType;
    }

    @Override
    public ArtifactWriteResult bumpVersion(BumpVersionCommand command) {
        String urn = command.artifactId().value();
        // A pinned identity is refused rather than reinterpreted: bumping from a version that is not
        // current has no sound answer, and bumping from the current one instead would quietly ignore
        // the version the caller named. :latest names the current version and is accepted.
        if (!UrnParser.isLatest(urn) && UrnParser.versionFromUrn(urn) != null) {
            throw new IllegalArgumentException(
                "bumpVersion needs a logical URN; " + urn + " pins a version. "
                + "Bump the artifact, then store the pin the bump returns.");
        }
        String logical = UrnParser.logicalUrn(urn);
        String assigned = registry.bumpVersion(logical, command.bump());
        dependencyGraph.registerFromRegistry(logical);
        var pin = new ArtifactId(assigned != null && !assigned.isBlank() ? assigned : logical);
        return new ArtifactWriteResult(pin, dependenciesOf(pin));
    }

    @Override
    public void deleteArtifact(ArtifactId artifactId, boolean cascade, boolean force) {
        elementCommandService.delete(artifactId.value(), cascade, force);
    }

    @Override
    public List<ArtifactSummary> orphans(ArtifactKind kind) {
        // Artifacts of this kind with no dataset-ref in-edge — search by type, then keep only those
        // the registry reports as members of zero DataSets.
        return search(new ArtifactSearchQuery(null, typeSegmentFor(kind), null, Integer.MAX_VALUE, 0)).stream()
            .filter(summary -> registry.dataSetMemberships(summary.artifactId().value()).isEmpty())
            .toList();
    }

    @Override
    public void linkToDataSet(ArtifactId dataSet, ArtifactId member) {
        ArtifactKind kind = memberKindOf(member.value());
        if (kind == null) {
            throw new IllegalArgumentException("Not a DataSet-member artifact: " + member.value());
        }
        linkMembersIntoDataSet(dataSet.value(), java.util.Map.of(member.value(), kind));
    }

    @Override
    public void unlinkFromDataSet(ArtifactId dataSet, ArtifactId member) {
        elementCommandService.unlinkFromDataSet(
            UrnParser.logicalUrn(dataSet.value()), UrnParser.logicalUrn(member.value()));
    }

    /** The registry {@code artifact_type} segment for a kind (matches the CORE URN's type segment). */
    private static String typeSegmentFor(ArtifactKind kind) {
        return switch (kind) {
            case MAPPING -> "mapping";
            case PIPELINE -> "pipeline";
            case DATA_SOURCE -> "datasource";
            case DATA_SINK -> "datasink";
            case DATA_STRUCTURE -> "datastructure";
            case DATA_SET -> "dataset";
            case ELEMENT -> "element";
        };
    }

    private static JsonNode withoutId(JsonNode content) {
        if (content instanceof tools.jackson.databind.node.ObjectNode object) {
            var copy = object.deepCopy();
            copy.remove("$id");
            return copy;
        }
        return content;
    }

    private static ValidationResult validationResult(List<Diagnostic> diagnostics) {
        return ValidationResult.of(diagnostics);
    }

    private static boolean hasErrors(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }
}
