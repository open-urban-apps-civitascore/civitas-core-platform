package de.civitascore.modelforge.application;

import tools.jackson.databind.JsonNode;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.contract.ArtifactSummary;
import de.civitascore.modelforge.contract.ArtifactView;
import de.civitascore.modelforge.contract.ArtifactWriteResult;
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
        var response = schemaImportService.importSchema(new SchemaImportRequest(command.schema()));
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
            new SaveArtifactCommand(new ArtifactId(urn), command.kind(), content, VersionBump.PATCH));
    }

    @Override
    public ArtifactWriteResult saveArtifact(SaveArtifactCommand command) {
        String urn = command.artifactId().value();
        // Opt-in structural validation: a document that declares a CORE $schema must satisfy it.
        // Documents without a (known) $schema are stored unvalidated — the transitional contract.
        List<Diagnostic> violations = coreSchemaValidator.validate(command.content());
        if (violations.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR)) {
            throw new ValidationFailedException(
                "Artifact does not satisfy its declared CORE $schema", violations);
        }
        // x-core-ref foreign keys are existence-checked on every write, not just on import: a save
        // may introduce a new reference, so the target must resolve now too. The check keys off the
        // content (it only does work when the document actually carries x-core-ref), so it needs no
        // artifact-kind branch; a lone save has no co-imported corpus beyond the artifact itself.
        List<Diagnostic> unresolvedRefs = referenceExistenceValidator.checkForeignKeys(
            command.content(), Set.of(UrnParser.logicalUrn(urn)));
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
            case ELEMENT -> command.content().isTextual()
                ? elementCommandService.storeXsd(urn, command.content().asText(), bump)
                : elementCommandService.storeJsonSchema(urn, withoutId(command.content()), bump);
            // The non-Element kinds store straight through the registry, which extracts and
            // persists their per-type reference edges (Mapping source/target, Pipeline nodes,
            // DataStructure/DataSet *Refs). registerFromRegistry() then mirrors those durable
            // edges into the in-memory graph so dependencies()/dependents() see them.
            case DATA_STRUCTURE -> { String p = registry.storeDataStructure(urn, command.content(), bump); dependencyGraph.registerFromRegistry(urn); yield p; }
            case MAPPING -> { String p = registry.storeMapping(urn, command.content(), bump); dependencyGraph.registerFromRegistry(urn); yield p; }
            case PIPELINE -> { String p = registry.storePipeline(urn, command.content(), bump); dependencyGraph.registerFromRegistry(urn); yield p; }
            case DATA_SOURCE -> { String p = registry.storeDataSource(urn, command.content(), bump); dependencyGraph.registerFromRegistry(urn); yield p; }
            case DATA_SINK -> { String p = registry.storeDataSink(urn, command.content(), bump); dependencyGraph.registerFromRegistry(urn); yield p; }
            case DATA_SET -> { String p = registry.storeDataSet(urn, command.content(), bump); dependencyGraph.registerFromRegistry(urn); yield p; }
        };
        // Defensive fallback (a registry double may return nothing): the honest unversioned
        // logical URN — deliberately NOT a resolveReference read-back.
        var pin = new ArtifactId(assigned != null && !assigned.isBlank()
            ? assigned : UrnParser.logicalUrn(urn));
        return new ArtifactWriteResult(pin, dependenciesOf(pin));
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
    public void deleteArtifact(ArtifactId artifactId, boolean cascade) {
        elementCommandService.delete(artifactId.value(), cascade);
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
