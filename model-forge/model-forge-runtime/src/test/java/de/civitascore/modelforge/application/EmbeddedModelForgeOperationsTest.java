package de.civitascore.modelforge.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.DependencyClosureView;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.contract.ArtifactKind;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.contract.BumpVersionCommand;
import de.civitascore.modelforge.contract.CreateArtifactCommand;
import de.civitascore.modelforge.contract.ImportSchemaCommand;
import de.civitascore.modelforge.contract.SaveArtifactCommand;
import de.civitascore.modelforge.contract.ValidationFailedException;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.core.port.ArtifactSearchResult;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.validation.ModelValidator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the facade operations added to expose search/save/delete beyond the original
 * import/read/validate/dependencies surface (needed by non-web consumers like the admin UI).
 */
class EmbeddedModelForgeOperationsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ArtifactRegistry registry;
    private DependencyGraphService graph;
    private ElementCommandService elementCommandService;
    private SchemaImportService schemaImportService;
    private ViewService viewService;
    private EmbeddedModelForgeOperations operations;

    @BeforeEach
    void setUp() {
        registry = mock(ArtifactRegistry.class);
        when(registry.inTransaction(any())).thenAnswer(call -> call.getArgument(0, Supplier.class).get());
        graph = mock(DependencyGraphService.class);
        var refExtractor = mock(SchemaRefExtractor.class);
        when(refExtractor.extractRefs(any())).thenReturn(Set.of());
        when(refExtractor.extractCoreRefTypes(any())).thenReturn(Set.of());
        var modelValidator = mock(ModelValidator.class);
        when(modelValidator.validateSchema(any())).thenReturn(List.of());
        elementCommandService = new ElementCommandService(registry, graph, refExtractor, modelValidator);
        var elementQueryService = new ElementQueryService(registry);
        schemaImportService = mock(SchemaImportService.class);
        viewService = mock(ViewService.class);
        var smartDataModelsService = mock(SmartDataModelsService.class);
        var xRepositoryService = mock(XRepositoryService.class);

        operations = new EmbeddedModelForgeOperations(
            schemaImportService,
            elementQueryService,
            elementCommandService,
            viewService,
            modelValidator,
            // These are orchestration tests (URN stamping, registry routing, dependency lists) — CORE
            // schema conformance is covered by CoreSchemaValidatorTest. Mock the validator so the
            // now-mandatory write-time validation doesn't reject the minimal fixtures used here.
            mock(de.civitascore.modelforge.validation.CoreSchemaValidator.class),
            new ReferenceExistenceValidator(registry, refExtractor),
            graph,
            registry,
            smartDataModelsService,
            xRepositoryService,
            new de.civitascore.modelforge.urn.UrnService("platform", "civitas", "common", "1.0.0")
        );
    }

    @Test
    void searchMapsRegistryHitsToArtifactSummaries() {
        when(registry.searchArtifacts(any())).thenReturn(List.of(
            new ArtifactSearchResult("urn:example:element:Sensor:1.0.0", "urn:example:element:Sensor",
                "element", "Sensor", "1.0.0", "json-schema", 1)
        ));

        var results = operations.search(new ArtifactSearchQuery("Sensor", null, null, 10, 0));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).artifactId()).isEqualTo(new ArtifactId("urn:example:element:Sensor:1.0.0"));
        assertThat(results.get(0).title()).isEqualTo("Sensor");
    }

    @Test
    void getBundledViewWithNoMaxDepthCallsTheUnboundedBundle() {
        var artifactId = new ArtifactId("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.0.0");
        when(viewService.bundle(artifactId.value())).thenReturn(Optional.of(mapper.createObjectNode()));

        operations.getBundledView(new de.civitascore.modelforge.contract.SchemaViewQuery(artifactId));

        verify(viewService).bundle(artifactId.value());
        verify(viewService, org.mockito.Mockito.never()).bundle(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void getBundledViewWithMaxDepthCallsTheBoundedBundle() {
        var artifactId = new ArtifactId("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.0.0");
        when(viewService.bundle(artifactId.value(), 2)).thenReturn(Optional.of(mapper.createObjectNode()));

        operations.getBundledView(new de.civitascore.modelforge.contract.SchemaViewQuery(artifactId, 2));

        verify(viewService).bundle(artifactId.value(), 2);
        verify(viewService, org.mockito.Mockito.never()).bundle(anyString());
    }

    @Test
    void getInlinedViewWithMaxDepthCallsTheBoundedInline() {
        var artifactId = new ArtifactId("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.0.0");
        when(viewService.inline(artifactId.value(), 3)).thenReturn(Optional.of(mapper.createObjectNode()));

        operations.getInlinedView(new de.civitascore.modelforge.contract.SchemaViewQuery(artifactId, 3));

        verify(viewService).inline(artifactId.value(), 3);
        verify(viewService, org.mockito.Mockito.never()).inline(anyString());
    }

    @Test
    void saveArtifactRoutesElementKindThroughElementCommandService() {
        JsonNode schema = mapper.createObjectNode().put("type", "object");
        var artifactId = new ArtifactId("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.0.0");
        // Model Forge assigns the version INSIDE the write and returns the pin with it — the
        // facade uses that returned pin directly, without any resolveReference read-back.
        when(registry.storeElement(any(), anySet(), anySet(), isNull(), eq(de.civitascore.modelforge.contract.VersionBump.PATCH), isNull()))
            .thenReturn("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.0.0");

        var result = operations.saveArtifact(
            new SaveArtifactCommand(artifactId, ArtifactKind.ELEMENT, schema, VersionBump.PATCH));

        assertThat(result.artifactId()).isEqualTo(artifactId);
        verify(registry).storeElement(any(), anySet(), anySet(), isNull(), eq(de.civitascore.modelforge.contract.VersionBump.PATCH), isNull());
        verify(registry, org.mockito.Mockito.never()).resolveReference(anyString());
    }

    @Test
    void saveArtifactRoutesTextualElementContentToXsdStorage() {
        // XSD is not a separate kind: an ELEMENT whose content is a textual node carries a raw XSD
        // document (rather than a JSON Schema object), so the save must route to the XSD storage
        // path. This is what the collapsed XSD_ELEMENT kind used to select explicitly.
        var artifactId = new ArtifactId("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.0.0");
        JsonNode xsd = StringNode.valueOf("<xs:schema/>");
        when(registry.extractImportRefs(any())).thenReturn(Set.of());
        when(registry.storeXsdElement(anyString(), eq("<xs:schema/>"), anySet(), any(de.civitascore.modelforge.contract.VersionBump.class)))
            .thenReturn("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.0.0");

        var result = operations.saveArtifact(
            new SaveArtifactCommand(artifactId, ArtifactKind.ELEMENT, xsd, VersionBump.PATCH));

        assertThat(result.artifactId()).isEqualTo(artifactId);
        verify(registry).storeXsdElement(anyString(), eq("<xs:schema/>"), anySet(), any(de.civitascore.modelforge.contract.VersionBump.class));
        verify(registry, org.mockito.Mockito.never())
            .storeElement(any(), anySet(), anySet(), isNull(), any(de.civitascore.modelforge.contract.VersionBump.class), isNull());
    }

    @Test
    void saveArtifactReturnsThePinAssignedByTheWriteWithoutReadBack() {
        // The pin in the write result must be exactly what the write itself assigned — no
        // resolveReference stubbing at all, and the save path must never call it. This pins the
        // fix for writes inside a surrounding (not-yet-committed) host transaction, where a
        // read-back could not see the write and returned an unversioned URN.
        JsonNode schema = mapper.createObjectNode().put("type", "object");
        var logicalId = new ArtifactId("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36");
        when(registry.storeElement(any(), anySet(), anySet(), isNull(), any(de.civitascore.modelforge.contract.VersionBump.class), isNull()))
            .thenReturn("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.3.0");

        var result = operations.saveArtifact(
            new SaveArtifactCommand(logicalId, ArtifactKind.ELEMENT, schema, VersionBump.MINOR));

        assertThat(result.artifactId().value())
            .isEqualTo("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.3.0");
        verify(registry, org.mockito.Mockito.never()).resolveReference(anyString());
    }

    @Test
    void saveArtifactRepointsAStaleVersionedIdAtTheTargetUrn() {
        // Content round-tripped from a fetched (pinned-version) artifact keeps its old $id (here
        // ":1.0.0"), even though the caller is saving against the logical (version-free) urn so
        // the registry can mint a fresh version. Saving it back must re-point $id at the target
        // urn, not silently keep serving the stale, now-outdated version number.
        var logicalArtifactId = new ArtifactId("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36");
        JsonNode staleContent = mapper.createObjectNode()
            .put("$id", "urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.0.0")
            .put("type", "object");

        var result = operations.saveArtifact(
            new SaveArtifactCommand(logicalArtifactId, ArtifactKind.ELEMENT, staleContent, VersionBump.PATCH));

        assertThat(result.artifactId()).isEqualTo(logicalArtifactId);
        var contentCaptor = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        verify(registry).storeElement(contentCaptor.capture(), anySet(), anySet(), isNull(), eq(de.civitascore.modelforge.contract.VersionBump.PATCH), isNull());
        assertThat(contentCaptor.getValue().path("$id").asText()).isEqualTo(logicalArtifactId.value());
    }

    @Test
    void saveArtifactRoutesNonElementKindsDirectlyToTheMatchingRegistryMethod() {
        JsonNode content = mapper.createObjectNode().put("id", "m1");
        var artifactId = new ArtifactId("urn:example:mapping:m1:1.0.0");
        // saveArtifact stamps $schema + the minted id into a COPY before storing, so the stored doc is
        // not the caller's original. This test asserts only the ROUTING (MAPPING → storeMapping); the
        // doc stamping itself is covered by createArtifactStampsTheMintedUrnIntoTheDocument.
        when(registry.storeMapping(anyString(), any(), eq(de.civitascore.modelforge.contract.VersionBump.MINOR)))
            .thenReturn("urn:example:mapping:m1:1.0.0");

        var result = operations.saveArtifact(
            new SaveArtifactCommand(artifactId, ArtifactKind.MAPPING, content, VersionBump.MINOR));

        assertThat(result.artifactId()).isEqualTo(artifactId);
        verify(registry).storeMapping(anyString(), any(), eq(de.civitascore.modelforge.contract.VersionBump.MINOR));
    }

    @Test
    void saveArtifactSyncsTheGraphFromDurableRefsForEveryNonElementKind() {
        // Each non-Element kind stores through the registry (which persists its per-type reference
        // edges) and must then mirror those durable edges into the in-memory graph, so a saved
        // mapping/pipeline/datastructure/dataset is navigable via dependencies()/dependents().
        record Case(ArtifactKind kind, String urn) {}
        var cases = List.of(
            new Case(ArtifactKind.MAPPING, "urn:core:platform:civitas:mapping:common:fe3634d2-c828-8bb9-a340-110e93f2b0ed:1.0.0"),
            new Case(ArtifactKind.PIPELINE, "urn:core:platform:civitas:pipeline:common:bd286352-a664-8e3d-b39e-1dbde504eb09:1.0.0"),
            new Case(ArtifactKind.DATA_STRUCTURE, "urn:core:platform:civitas:datastructure:common:d9d0d267-d8e1-8d73-8b58-4307281607ac:1.0.0"),
            new Case(ArtifactKind.DATA_SOURCE, "urn:core:platform:civitas:datasource:common:0169fd0b-f33d-87aa-a20b-4c9e5a08e4b9:1.0.0"),
            new Case(ArtifactKind.DATA_SINK, "urn:core:platform:civitas:datasink:common:c0a47004-aa7a-8414-a7cd-a5161a6d90a0:1.0.0"),
            new Case(ArtifactKind.DATA_SET, "urn:core:platform:civitas:dataset:common:e159136c-26ed-817e-a2eb-92a0deca8a73:1.0.0"));

        for (Case c : cases) {
            var artifactId = new ArtifactId(c.urn());
            operations.saveArtifact(new SaveArtifactCommand(
                artifactId, c.kind(), mapper.createObjectNode(), VersionBump.PATCH));
            verify(graph).registerFromRegistry(c.urn());
        }
    }

    @Test
    void saveArtifactDoesNotDoubleRegisterElementKindsInTheGraph() {
        // Element/XSD register their graph edges inside ElementCommandService, so saveArtifact
        // must NOT additionally call registerFromRegistry for them (that path resolves refs from
        // content, not the durable table).
        var artifactId = new ArtifactId("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.0.0");

        operations.saveArtifact(new SaveArtifactCommand(
            artifactId, ArtifactKind.ELEMENT, mapper.createObjectNode().put("type", "object"), VersionBump.PATCH));

        verify(graph, org.mockito.Mockito.never()).registerFromRegistry(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void deleteArtifactDelegatesToElementCommandService() {
        var artifactId = new ArtifactId("urn:example:element:Sensor:1.0.0");

        operations.deleteArtifact(artifactId);

        verify(registry).deleteArtifact(artifactId.value());
    }

    @Test
    void deleteArtifactCascadeRemovesOrphanedMembersButKeepsSharedOnes() {
        var container = new ArtifactId("urn:core:platform:civitas:datastructure:common:d9d0d267-d8e1-8d73-8b58-4307281607ac:1.0.0");
        String orphan = "urn:core:platform:civitas:element:common:ce0ed78d-6fea-8664-9c88-a4c3af6514b6:1.0.0";
        String shared = "urn:core:platform:civitas:element:common:8de637bf-7014-8787-8011-0f0f80435975:1.0.0";
        String orphanLogical = UrnParser.logicalUrn(orphan);
        String sharedLogical = UrnParser.logicalUrn(shared);

        when(registry.nonDataSetBlockingDependents(container.value())).thenReturn(List.of());
        when(registry.ownedMemberUrns(container.value())).thenReturn(List.of(orphanLogical, sharedLogical));
        // Once the container is gone, A is orphaned but B is still grouped by another DataStructure.
        when(registry.nonDataSetBlockingDependents(orphanLogical)).thenReturn(List.of());
        when(registry.nonDataSetBlockingDependents(sharedLogical))
            .thenReturn(List.of("urn:core:platform:civitas:datastructure:common:90a887a9-b328-81d7-a937-fb00dda6c8c7"));
        when(registry.fetch(orphanLogical)).thenReturn(Optional.of(mapper.createObjectNode()));
        when(registry.fetch(sharedLogical)).thenReturn(Optional.of(mapper.createObjectNode()));
        when(registry.ownedMemberUrns(orphanLogical)).thenReturn(List.of());

        operations.deleteArtifact(container, true);

        verify(registry).deleteArtifact(container.value());
        verify(registry).deleteArtifact(orphanLogical);
        verify(registry, org.mockito.Mockito.never()).deleteArtifact(shared);
        verify(registry, org.mockito.Mockito.never()).deleteArtifact(sharedLogical);
    }

    /** A Data Set the caller holds, the Mapping it names, and the Data Set that holds that Mapping. */
    private static final ArtifactId MINE =
        new ArtifactId("urn:core:platform:civitas:dataset:common:53609354-f43b-8d83-8ea5-f0867fb76a09");
    private static final ArtifactId MAPPING =
        new ArtifactId("urn:core:platform:civitas:mapping:common:68aed840-8777-8fb5-9267-48d46584ca9a");
    private static final String THEIRS = "urn:core:platform:civitas:dataset:common:b10a5e90-2904-8222-bf45-4e9669fd2bf8";

    private void manifestExists() {
        when(registry.fetch(MINE.value())).thenReturn(Optional.of(mapper.createObjectNode()));
    }

    @Test
    void linkingAMappingADifferentDataSetHoldsIsRefused() {
        manifestExists();
        when(registry.fetch(MAPPING.value())).thenReturn(Optional.of(mapper.createObjectNode()));
        when(registry.dataSetMemberships(MAPPING.value())).thenReturn(List.of(THEIRS));

        assertThatThrownBy(() -> operations.linkToDataSet(MINE, MAPPING))
            .isInstanceOf(ValidationFailedException.class)
            .as("the Data Set holding it stays unnamed")
            .hasMessageNotContaining(THEIRS);
        verify(registry, org.mockito.Mockito.never()).storeDataSet(anyString(), any(), any());
    }

    @Test
    void aMappingThatDoesNotExistIsRefusedInTheSameWords() {
        manifestExists();
        when(registry.fetch(MAPPING.value())).thenReturn(Optional.empty());

        String absent = catchThrowable(() -> operations.linkToDataSet(MINE, MAPPING)).getMessage();

        when(registry.fetch(MAPPING.value())).thenReturn(Optional.of(mapper.createObjectNode()));
        when(registry.dataSetMemberships(MAPPING.value())).thenReturn(List.of(THEIRS));
        String taken = catchThrowable(() -> operations.linkToDataSet(MINE, MAPPING)).getMessage();

        // Telling the two apart would say which Mapping URNs are taken.
        assertThat(absent).isEqualTo(taken);
    }

    @Test
    void linkingAMappingOnlyItsOwnDataSetHoldsIsAllowed() {
        manifestExists();
        when(registry.fetch(MAPPING.value())).thenReturn(Optional.of(mapper.createObjectNode()));
        when(registry.dataSetMemberships(MAPPING.value())).thenReturn(List.of(MINE.value()));

        operations.linkToDataSet(MINE, MAPPING);

        verify(registry).storeDataSet(eq(MINE.value()), any(), eq(VersionBump.MINOR));
    }

    @Test
    void linkingADataSinkADifferentDataSetHoldsIsAllowed() {
        var sink = new ArtifactId("urn:core:platform:civitas:datasink:common:a18ebf5b-e74f-8a45-ac85-f72d87f3935f");
        manifestExists();

        operations.linkToDataSet(MINE, sink);

        // The one-Data-Set rule is the Mapping's alone: every other kind has a record and rights of
        // its own, so sharing one discloses nothing.
        verify(registry).storeDataSet(eq(MINE.value()), any(), eq(VersionBump.MINOR));
    }

    @Test
    void deletionBlockersNamesTheReferrersThatWouldRefuseTheDelete() {
        var artifactId = new ArtifactId("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36");
        String mapping = "urn:core:platform:civitas:mapping:common:452b4712-5365-8847-ae8a-8b7340004283";
        when(registry.nonDataSetBlockingDependents(artifactId.value(), null)).thenReturn(List.of(mapping));

        assertThat(operations.deletionBlockers(artifactId)).containsExactly(mapping);
    }

    @Test
    void deletionBlockersIgnoresAReferenceHeldFromInsideTheDeletedSet() {
        var grouping = new ArtifactId("urn:core:platform:civitas:datastructure:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36");
        String member = "urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36";
        when(registry.ownedMemberUrns(grouping.value(), null)).thenReturn(List.of(member));
        // The grouping is the only thing referencing its own member, and the same delete removes it.
        when(registry.nonDataSetBlockingDependents(member, null)).thenReturn(List.of(grouping.value()));

        assertThat(operations.deletionBlockers(grouping)).isEmpty();
    }

    @Test
    void deletionBlockersNamesAReferrerOfAnOwnedMember() {
        var grouping = new ArtifactId("urn:core:platform:civitas:datastructure:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36");
        String member = "urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36";
        String mapping = "urn:core:platform:civitas:mapping:common:452b4712-5365-8847-ae8a-8b7340004283";
        when(registry.ownedMemberUrns(grouping.value(), null)).thenReturn(List.of(member));
        when(registry.nonDataSetBlockingDependents(member, null))
            .thenReturn(List.of(grouping.value(), mapping));

        assertThat(operations.deletionBlockers(grouping)).containsExactly(mapping);
    }

    @Test
    void deletionBlockersNamesTheDataSetsOnlyOnceAMemberIsSharedByTwo() {
        var artifactId = new ArtifactId("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36");
        String one = "urn:core:platform:civitas:dataset:common:1b662900-f924-8fc1-bf29-4632ef683a54";
        String two = "urn:core:platform:civitas:dataset:common:75f8ad04-c392-883d-b926-8275b00b172f";
        when(registry.nonDataSetBlockingDependents(artifactId.value(), null)).thenReturn(List.of());

        // A member of a single Data Set is deleted and unlinked from it, so that membership is no
        // obstacle and must not be reported as one.
        when(registry.dataSetMemberships(artifactId.value())).thenReturn(List.of(one));
        assertThat(operations.deletionBlockers(artifactId)).isEmpty();

        when(registry.dataSetMemberships(artifactId.value())).thenReturn(List.of(one, two));
        assertThat(operations.deletionBlockers(artifactId)).containsExactly(one, two);
    }

    @Test
    void deletionBlockersIsEmptyWhenNothingStandsInTheWay() {
        var artifactId = new ArtifactId("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36");
        when(registry.nonDataSetBlockingDependents(artifactId.value(), null)).thenReturn(List.of());
        when(registry.dataSetMemberships(artifactId.value())).thenReturn(List.of());

        assertThat(operations.deletionBlockers(artifactId)).isEmpty();
    }

    @Test
    void deleteArtifactIsBlockedWhileDependentsExist() {
        var artifactId = new ArtifactId("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.0.0");
        when(registry.nonDataSetBlockingDependents(artifactId.value()))
            .thenReturn(List.of("urn:core:platform:civitas:mapping:common:452b4712-5365-8847-ae8a-8b7340004283"));

        assertThatThrownBy(() -> operations.deleteArtifact(artifactId))
            .isInstanceOf(de.civitascore.modelforge.contract.ArtifactInUseException.class)
            .hasMessageContaining("urn:core:platform:civitas:mapping:common:452b4712-5365-8847-ae8a-8b7340004283");

        verify(registry, org.mockito.Mockito.never()).deleteArtifact(any());
    }

    @Test
    void importSchemaReturnsTheConcreteVersionedPinAssignedByTheRegistry() {
        // The import service carries the registry-assigned pin (returned by the write itself)
        // through in its resourceId; the facade must return it VERBATIM — no resolveReference
        // read-back — so a host pins exactly what was stored, even mid-transaction.
        when(schemaImportService.importSchema(any())).thenReturn(new SchemaImportResult("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.2.0", List.of()));

        var result = operations.importSchema(new ImportSchemaCommand(mapper.createObjectNode()));

        assertThat(result.rootArtifactId().value())
            .isEqualTo("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.2.0");
        assertThat(UrnParser.versionFromUrn(result.rootArtifactId().value())).isEqualTo("1.2.0");
        assertThat(result.importedArtifactIds()).containsExactly(result.rootArtifactId());
        verify(registry, org.mockito.Mockito.never()).resolveReference(anyString());
    }

    @Test
    void importSchemaFallsBackToResolveReferenceOnlyWhenTheResultCarriesNoVersionedPin() {
        // Only a version-less resourceId (e.g. a registry double that returned no pin) may use
        // the resolveReference fallback.
        when(schemaImportService.importSchema(any())).thenReturn(new SchemaImportResult("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36", List.of()));
        when(registry.resolveReference("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36"))
            .thenReturn(Optional.of("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.2.0"));

        var result = operations.importSchema(new ImportSchemaCommand(mapper.createObjectNode()));

        assertThat(result.rootArtifactId().value())
            .isEqualTo("urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.2.0");
    }

    @Test
    void getArtifactForwardsAVersionedUrnForVersionPreciseReads() {
        // A versioned ArtifactId must read exactly that version: the facade forwards it verbatim
        // to the registry (no coercion to the logical/current URN).
        String versioned = "urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.0.0";
        JsonNode content = mapper.createObjectNode().put("$id", versioned);
        when(registry.fetch(versioned)).thenReturn(Optional.of(content));

        var view = operations.getArtifact(new ArtifactId(versioned));

        assertThat(view).isPresent();
        assertThat(view.get().artifactId().value()).isEqualTo(versioned);
        assertThat(view.get().content()).isEqualTo(content);
        verify(registry).fetch(versioned);
    }

    // ── createArtifact: minted identities for the non-Element kinds ──────────────

    @Test
    void createArtifactMintsDistinctIdentitiesForEqualNames() {
        // The write itself returns the assigned versioned pin.
        when(registry.storeMapping(anyString(), any(), any()))
            .thenAnswer(inv -> UrnParser.withVersion(UrnParser.logicalUrn(inv.getArgument(0)), "1.0.0"));
        JsonNode content = mapper.createObjectNode().put("source", "urn:core:x");

        var first = operations.createArtifact(
            new CreateArtifactCommand(ArtifactKind.MAPPING, "Sensor to Obs", content)).artifactId();
        var second = operations.createArtifact(
            new CreateArtifactCommand(ArtifactKind.MAPPING, "Sensor to Obs", content)).artifactId();

        // Equal display names → two independent artifacts, each with a minted, versioned pin.
        assertThat(UrnParser.idFromUrn(first.value())).isNotNull();
        assertThat(UrnParser.versionFromUrn(first.value())).isEqualTo("1.0.0");
        assertThat(UrnParser.logicalUrn(first.value())).isNotEqualTo(UrnParser.logicalUrn(second.value()));
    }

    @Test
    void createArtifactBuildsTheUrnFromTheGivenId() {
        when(registry.storeDataSink(anyString(), any(), any()))
            .thenAnswer(inv -> UrnParser.withVersion(UrnParser.logicalUrn(inv.getArgument(0)), "1.0.0"));
        UUID sinkId = UUID.fromString("6f1d2c3b-4a5e-4f60-8172-93a4b5c6d7e8");

        var pin = operations.createArtifact(new CreateArtifactCommand(
            ArtifactKind.DATA_SINK, "Readings", mapper.createObjectNode(), null, sinkId)).artifactId();

        assertThat(pin.value())
            .isEqualTo("urn:core:platform:civitas:datasink:common:" + sinkId + ":1.0.0");
    }

    /** Without this, an artifact created from a document without a title is listed by its UUID. */
    @Test
    void createArtifactRecordsTheDisplayNameOnlyWhenTheDocumentHasNoTitle() {
        when(registry.storeMapping(anyString(), any(), any()))
            .thenAnswer(inv -> UrnParser.withVersion(UrnParser.logicalUrn(inv.getArgument(0)), "1.0.0"));

        var untitled = operations.createArtifact(new CreateArtifactCommand(
            ArtifactKind.MAPPING, "Sensor to Obs", mapper.createObjectNode())).artifactId();
        operations.createArtifact(new CreateArtifactCommand(
            ArtifactKind.MAPPING, "Ignored", mapper.createObjectNode().put("title", "Own title")));

        verify(registry).nameArtifact(untitled.value(), "Sensor to Obs");
        verify(registry, never()).nameArtifact(anyString(), eq("Ignored"));
    }

    /** Without this, an artifact created from a document with an empty title is listed by its UUID. */
    @Test
    void createArtifactRecordsTheDisplayNameWhenTheDocumentTitleIsBlank() {
        when(registry.storeMapping(anyString(), any(), any()))
            .thenAnswer(inv -> UrnParser.withVersion(UrnParser.logicalUrn(inv.getArgument(0)), "1.0.0"));

        var blankTitled = operations.createArtifact(new CreateArtifactCommand(
            ArtifactKind.MAPPING, "Sensor to Obs", mapper.createObjectNode().put("title", " "))).artifactId();

        verify(registry).nameArtifact(blankTitled.value(), "Sensor to Obs");
    }

    @Test
    void writeResultsCarryTheDependencyListsGroupedByReferenceType() {
        when(registry.storeMapping(anyString(), any(), any()))
            .thenAnswer(inv -> UrnParser.withVersion(UrnParser.logicalUrn(inv.getArgument(0)), "1.0.0"));
        String source = "urn:core:platform:civitas:element:common:4c7d57f1-1920-8101-98cb-4977899ef805:1.0.0";
        String target = "urn:core:platform:civitas:element:common:3d98ebef-958f-8ca9-91c9-75a18a383382:1.0.0";
        when(registry.referencesByType(anyString())).thenReturn(java.util.Map.of(
            "mapping-source", List.of(source),
            "mapping-target", List.of(target)));
        JsonNode content = mapper.createObjectNode().put("source", source).put("target", target);

        var result = operations.createArtifact(
            new CreateArtifactCommand(ArtifactKind.MAPPING, "Raw to Obs", content));

        // The HAL-link-style lists a host mirrors: stored reference type → target pins.
        assertThat(result.dependencies())
            .containsEntry("mapping-source", List.of(new ArtifactId(source)))
            .containsEntry("mapping-target", List.of(new ArtifactId(target)));
        verify(registry).referencesByType(result.artifactId().value());
    }

    @Test
    void createArtifactStampsTheMintedUrnIntoTheDocument() {
        when(registry.storePipeline(anyString(), any(), any()))
            .thenAnswer(inv -> UrnParser.withVersion(UrnParser.logicalUrn(inv.getArgument(0)), "1.0.0"));
        // A caller-supplied id is stale by definition — Model Forge owns the created identity.
        JsonNode content = mapper.createObjectNode().put("id", "urn:core:something:stale");

        operations.createArtifact(new CreateArtifactCommand(ArtifactKind.PIPELINE, "Import Flow", content));

        var urn = org.mockito.ArgumentCaptor.forClass(String.class);
        var stored = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        verify(registry).storePipeline(urn.capture(), stored.capture(),
            eq(de.civitascore.modelforge.contract.VersionBump.PATCH));
        assertThat(stored.getValue().path("id").asString()).isEqualTo(urn.getValue());
        assertThat(UrnParser.idFromUrn(urn.getValue())).isNotNull();
        verify(graph).registerFromRegistry(urn.getValue());
    }

    @Test
    void createArtifactRejectsElementKinds() {
        JsonNode schema = mapper.createObjectNode().put("type", "object");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                operations.createArtifact(new CreateArtifactCommand(ArtifactKind.ELEMENT, "Sensor", schema)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("importSchema");
    }

    /**
     * The change class an import requests, and the version it may claim for a brand-new artifact,
     * both reach the import service. Without them on the command there is nowhere to carry either, so
     * every import is a patch and a host cannot open a draft lane below the registry's first version.
     */
    @Test
    void importCommand_carriesTheChangeClassAndAnInitialVersion() {
        JsonNode schema = mapper.createObjectNode().put("title", "Thing");
        when(schemaImportService.importSchema(any(SchemaImportRequest.class)))
            .thenReturn(new SchemaImportResult(
                "urn:core:platform:civitas:element:common:690ab666-6722-8a06-a895-d45f638ed26a:0.1.0", List.of(), List.of()));

        operations.importSchema(new ImportSchemaCommand(schema, VersionBump.MAJOR, "0.1.0", true));

        ArgumentCaptor<SchemaImportRequest> request = ArgumentCaptor.forClass(SchemaImportRequest.class);
        verify(schemaImportService).importSchema(request.capture());
        assertThat(request.getValue().bump()).isEqualTo(VersionBump.MAJOR);
        assertThat(request.getValue().version()).isEqualTo("0.1.0");
        assertThat(request.getValue().preserveVersion()).isTrue();
    }

    /**
     * Without the stamping, a client-authored $id or member $id decides which DataStructure and which
     * Elements an import overwrites.
     */
    @Test
    void importCommand_withADataStructureId_replacesTheDocumentAndMemberIds() {
        UUID dataStructureId = UUID.fromString("2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d");
        ObjectNode schema = mapper.createObjectNode()
            .put("$id", "urn:core:platform:civitas:datastructure:common:690ab666-6722-8a06-a895-d45f638ed26a")
            .put("$ref", "#/$defs/Sensor");
        schema.putObject("$defs").putObject("Sensor")
            .put("$id", "urn:core:platform:civitas:element:common:4c7d57f1-1920-8101-98cb-4977899ef805")
            .put("type", "object");
        when(schemaImportService.importSchema(any(SchemaImportRequest.class)))
            .thenReturn(new SchemaImportResult(
                "urn:core:platform:civitas:datastructure:common:" + dataStructureId + ":1.0.0", List.of(), List.of()));

        operations.importSchema(
            new ImportSchemaCommand(schema, VersionBump.MAJOR, null, false, null, dataStructureId));

        ArgumentCaptor<SchemaImportRequest> request = ArgumentCaptor.forClass(SchemaImportRequest.class);
        verify(schemaImportService).importSchema(request.capture());
        JsonNode imported = request.getValue().schema();
        assertThat(imported.path("$id").asString())
            .isEqualTo("urn:core:platform:civitas:datastructure:common:" + dataStructureId);
        assertThat(imported.path("$defs").path("Sensor").has("$id")).isFalse();
        assertThat(imported.path("$defs").path("Sensor").path("type").asString()).isEqualTo("object");
    }

    /**
     * A PATCH or a client may send the stored or the bundled form back. Treated as a shaped document,
     * it would become an Element; kept, the embedded member copies would become members of their own.
     */
    @Test
    void importCommand_withADataStructureId_keepsTheStoredFormADataStructure() {
        UUID dataStructureId = UUID.fromString("2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d");
        String member = "urn:core:platform:civitas:element:common:4c7d57f1-1920-8101-98cb-4977899ef805:1.0.0";
        ObjectNode schema = mapper.createObjectNode()
            .put("$id", "urn:core:platform:civitas:datastructure:common:" + dataStructureId)
            .put("$ref", member);
        ObjectNode defs = schema.putObject("$defs");
        defs.putObject("Sensor").put("$ref", member);
        defs.putObject("urn:core:platform:civitas:element:common:4c7d57f1-1920-8101-98cb-4977899ef805")
            .put("$id", member)
            .put("type", "object");
        when(schemaImportService.importSchema(any(SchemaImportRequest.class)))
            .thenReturn(new SchemaImportResult(
                "urn:core:platform:civitas:datastructure:common:" + dataStructureId + ":1.0.0", List.of(), List.of()));

        operations.importSchema(
            new ImportSchemaCommand(schema, VersionBump.MINOR, null, false, "1.0.0", dataStructureId));

        ArgumentCaptor<SchemaImportRequest> request = ArgumentCaptor.forClass(SchemaImportRequest.class);
        verify(schemaImportService).importSchema(request.capture());
        JsonNode imported = request.getValue().schema();
        assertThat(imported.path("$id").asString())
            .isEqualTo("urn:core:platform:civitas:datastructure:common:" + dataStructureId);
        assertThat(imported.path("$defs").propertyNames()).containsExactly("Sensor");
    }

    /** Folded into a DataStructure, a document with a shape of its own would lose that shape. */
    @Test
    void importCommand_withADataStructureId_importsAShapedDocumentAsItsRootElement() {
        UUID dataStructureId = UUID.fromString("2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d");
        ObjectNode schema = mapper.createObjectNode().put("title", "Reading").put("type", "object");
        when(schemaImportService.importSchema(any(SchemaImportRequest.class)))
            .thenReturn(new SchemaImportResult(
                "urn:core:platform:civitas:element:common:" + dataStructureId + ":1.0.0", List.of(), List.of()));

        operations.importSchema(
            new ImportSchemaCommand(schema, VersionBump.MAJOR, null, false, null, dataStructureId));

        ArgumentCaptor<SchemaImportRequest> request = ArgumentCaptor.forClass(SchemaImportRequest.class);
        verify(schemaImportService).importSchema(request.capture());
        assertThat(request.getValue().schema().path("$id").asString())
            .isEqualTo("urn:core:platform:civitas:element:common:" + dataStructureId);
        assertThat(request.getValue().schema().path("type").asString()).isEqualTo("object");
    }

    /** An import that asks for nothing keeps the registry's own defaults. */
    @Test
    void importCommand_defaultsToPatchAndNoClaimedVersion() {
        JsonNode schema = mapper.createObjectNode().put("title", "Thing");
        when(schemaImportService.importSchema(any(SchemaImportRequest.class)))
            .thenReturn(new SchemaImportResult(
                "urn:core:platform:civitas:element:common:690ab666-6722-8a06-a895-d45f638ed26a:1.0.0", List.of(), List.of()));

        operations.importSchema(new ImportSchemaCommand(schema));

        ArgumentCaptor<SchemaImportRequest> request = ArgumentCaptor.forClass(SchemaImportRequest.class);
        verify(schemaImportService).importSchema(request.capture());
        assertThat(request.getValue().bump()).isEqualTo(VersionBump.PATCH);
        assertThat(request.getValue().preserveVersion()).isFalse();
    }

    /**
     * A release carries the current version forward at the class the caller chose, and the pin it
     * returns is the one the registry assigned.
     */
    @Test
    void bumpVersion_carriesTheCurrentVersionForwardAtTheRequestedClass() {
        String logical = "urn:core:platform:civitas:datastructure:common:4e0604c3-1372-884e-91a3-68ed0ec7aa03";
        when(registry.bumpVersion(logical, VersionBump.MAJOR)).thenReturn(logical + ":2.0.0");
        when(registry.referencesByType(logical + ":2.0.0")).thenReturn(Map.of());

        var result = operations.bumpVersion(
            new BumpVersionCommand(new ArtifactId(logical), VersionBump.MAJOR));

        assertThat(result.artifactId().value()).isEqualTo(logical + ":2.0.0");
        verify(registry).bumpVersion(logical, VersionBump.MAJOR);
    }

    /** {@code :latest} names the current version, so it is a usable identity for a bump. */
    @Test
    void bumpVersion_acceptsLatest() {
        String logical = "urn:core:platform:civitas:datastructure:common:4e0604c3-1372-884e-91a3-68ed0ec7aa03";
        when(registry.bumpVersion(logical, VersionBump.MINOR)).thenReturn(logical + ":1.1.0");
        when(registry.referencesByType(logical + ":1.1.0")).thenReturn(Map.of());

        operations.bumpVersion(
            new BumpVersionCommand(new ArtifactId(logical + ":latest"), VersionBump.MINOR));

        verify(registry).bumpVersion(logical, VersionBump.MINOR);
    }

    /**
     * Bumping from a version that is not current has no sound answer, and bumping from the current
     * one instead would ignore the version the caller named, so a pinned identity is refused.
     */
    @Test
    void bumpVersion_refusesAPinnedIdentity() {
        var command = new BumpVersionCommand(
            new ArtifactId("urn:core:platform:civitas:datastructure:common:4e0604c3-1372-884e-91a3-68ed0ec7aa03:1.0.0"),
            VersionBump.MINOR);

        assertThatThrownBy(() -> operations.bumpVersion(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pins a version");
    }

    // ── closure / existing ────────────────────────────────────────────────────

    private static final String CLOSURE_ROOT =
        "urn:core:platform:civitas:pipeline:common:a8ff27eb-c6c3-8136-9fac-596b9b92356a:1.0.0";
    private static final String CLOSURE_MEMBER =
        "urn:core:platform:civitas:datastructure:common:6ddf8001-3ba6-83db-8d23-b547419c1ff2:1.0.0";
    private static final String CLOSURE_GHOST =
        "urn:core:platform:civitas:element:common:158a41e4-f47b-8e87-bc6f-d6573a02ab99:1.0.0";

    @Test
    void closureRejectsAQueryWithoutADepthBound() {
        assertThatThrownBy(() -> operations.closure(new DependencyQuery(new ArtifactId(CLOSURE_ROOT))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxDepth");
        verifyNoInteractions(graph);
    }

    @Test
    void closureCarriesWhetherTheBoundCutTheWalkShort() {
        when(graph.getTransitiveDependenciesBounded(CLOSURE_ROOT, 4))
            .thenReturn(new DependencyGraphService.BoundedWalk(Set.of(CLOSURE_MEMBER), true));
        when(registry.heldUrns(any())).thenReturn(Set.of(CLOSURE_MEMBER));

        assertThat(operations.closure(new DependencyQuery(new ArtifactId(CLOSURE_ROOT), 4)).truncated())
            .isTrue();
    }

    @Test
    void closureWalksToTheRequestedDepth() {
        when(graph.getTransitiveDependenciesBounded(CLOSURE_ROOT, 4))
            .thenReturn(new DependencyGraphService.BoundedWalk(Set.of(CLOSURE_MEMBER), false));
        when(registry.heldUrns(any())).thenReturn(Set.of(CLOSURE_MEMBER));

        operations.closure(new DependencyQuery(new ArtifactId(CLOSURE_ROOT), 4));

        verify(graph).getTransitiveDependenciesBounded(CLOSURE_ROOT, 4);
        verify(graph, never()).getDependencies(any());
    }

    @Test
    void closureReportsTheUnheldMembersFromOneProbe() {
        when(graph.getTransitiveDependenciesBounded(CLOSURE_ROOT, 10))
            .thenReturn(new DependencyGraphService.BoundedWalk(new LinkedHashSet<>(List.of(CLOSURE_MEMBER, CLOSURE_GHOST)), false));
        when(registry.heldUrns(any())).thenReturn(Set.of(CLOSURE_MEMBER));

        var view = operations.closure(new DependencyQuery(new ArtifactId(CLOSURE_ROOT), 10));

        assertThat(view.closure()).extracting(ArtifactId::value)
            .containsExactly(CLOSURE_MEMBER, CLOSURE_GHOST);
        assertThat(view.unresolved()).extracting(ArtifactId::value).containsExactly(CLOSURE_GHOST);
        assertThat(view.complete()).isFalse();
        verify(registry, times(1)).heldUrns(any());
    }

    @Test
    void closureExcludesEveryVersionOfTheRootEvenWhenACycleReachesIt() {
        String otherRootVersion =
            "urn:core:platform:civitas:pipeline:common:a8ff27eb-c6c3-8136-9fac-596b9b92356a:2.0.0";
        when(graph.getTransitiveDependenciesBounded(CLOSURE_ROOT, 10))
            .thenReturn(new DependencyGraphService.BoundedWalk(new LinkedHashSet<>(List.of(CLOSURE_MEMBER, CLOSURE_ROOT, otherRootVersion)), false));
        when(registry.heldUrns(any())).thenReturn(Set.of(CLOSURE_MEMBER));

        var view = operations.closure(new DependencyQuery(new ArtifactId(CLOSURE_ROOT), 10));

        assertThat(view.closure()).extracting(ArtifactId::value).containsExactly(CLOSURE_MEMBER);
        assertThat(view.complete()).isTrue();
    }

    @Test
    void existingReturnsOnlyTheHeldSubsetInInputOrder() {
        when(registry.heldUrns(any())).thenReturn(Set.of(CLOSURE_GHOST, CLOSURE_MEMBER));

        var held = operations.existing(
            List.of(new ArtifactId(CLOSURE_MEMBER), new ArtifactId(CLOSURE_ROOT), new ArtifactId(CLOSURE_GHOST)));

        assertThat(held).extracting(ArtifactId::value).containsExactly(CLOSURE_MEMBER, CLOSURE_GHOST);
        verify(registry, times(1)).heldUrns(any());
    }

    @Test
    void existingWithNothingAskedTouchesNoRegistry() {
        assertThat(operations.existing(List.of())).isEmpty();
        assertThat(operations.existing(null)).isEmpty();
        verify(registry, never()).heldUrns(any());
    }

    // ── The version a write publishes to the graph ────────────────────────────

    private static final String REVISED_LOGICAL = "urn:core:platform:civitas:datastructure:common:9f0b96e4-efad-8212-b771-1e514454b7cc";
    private static final String REVISED_PIN     = REVISED_LOGICAL + ":1.1.0";
    private static final String REVISED_CURRENT = REVISED_LOGICAL + ":2.0.0";
    private static final String REVISED_MEMBER  = "urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.0.0";

    @Test
    void writeOntoAnOlderVersionLinePublishesThatVersion() {
        // A revision of a line that is not the newest keeps its own major and stays behind the
        // current version. The node has to be the version that was written.
        var realGraph = new DependencyGraphService(registry);
        var ops = operationsWith(realGraph);
        when(registry.storeDataStructure(eq(REVISED_LOGICAL), any(), any())).thenReturn(REVISED_PIN);
        when(registry.resolveReference(REVISED_LOGICAL)).thenReturn(Optional.of(REVISED_CURRENT));
        when(registry.fetchArtifactRefUrns(REVISED_PIN)).thenReturn(List.of(REVISED_MEMBER));
        when(registry.fetchArtifactRefUrns(REVISED_CURRENT)).thenReturn(List.of());

        ops.saveArtifact(new SaveArtifactCommand(
            new ArtifactId(REVISED_LOGICAL), ArtifactKind.DATA_STRUCTURE, mapper.createObjectNode(),
            VersionBump.MINOR));

        assertThat(realGraph.getDependencies(REVISED_PIN)).containsExactly(REVISED_MEMBER);
        assertThat(realGraph.getDependencies(REVISED_CURRENT))
            .as("the current version was not written and must keep its own edges")
            .isEmpty();
    }

    /** The same operations, wired to a graph the test can read back. */
    private EmbeddedModelForgeOperations operationsWith(DependencyGraphService realGraph) {
        return new EmbeddedModelForgeOperations(
            schemaImportService, new ElementQueryService(registry), elementCommandService, viewService,
            mock(ModelValidator.class), mock(de.civitascore.modelforge.validation.CoreSchemaValidator.class),
            new ReferenceExistenceValidator(registry, mock(SchemaRefExtractor.class)),
            realGraph, registry, mock(SmartDataModelsService.class), mock(XRepositoryService.class),
            new de.civitascore.modelforge.urn.UrnService("platform", "civitas", "common", "1.0.0"));
    }
}
