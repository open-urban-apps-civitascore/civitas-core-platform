package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSetImportInputDTO;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSinkImportInputDTO;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.input.DataSourceImportInputDTO;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataStructureImportInputDTO;
import de.civitascore.portal.model.input.MappingImportInputDTO;
import de.civitascore.portal.model.input.PipelineImportInputDTO;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.DataSetImportOutputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.service.DataStructureImportService.ImportResolution;
import de.civitascore.portal.service.MappingImportService.MappingResolution;
import de.civitascore.portal.util.InvalidInputException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link DataSetImportService}: orchestration order (structures → sources → mappings
 * → dataset → sinks → pipelines → manifest links), URN-based structure resolution for sources,
 * mappings and sink elements (bundle first, then installed), and the pipeline graph rewrite that
 * turns bundle-local name references into the created artifacts' minted URNs. The collaborating
 * services are mocked; their own guards are covered in {@link DataStructureImportServiceTest} and
 * {@link MappingImportServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class DataSetImportServiceTest {

  private static final String STRUCTURE_URN =
      "urn:core:standard:openurbanapps:datastructure:environment:airqualitystation:default";
  private static final String VERSIONED_URN = STRUCTURE_URN + ":1.0.0";
  private static final String MAPPING_URN =
      "urn:core:standard:openurbanapps:mapping:environment:stationtoobservation:default";
  private static final String MAPPING_VERSIONED_URN = MAPPING_URN + ":1.0.0";
  private static final String MANIFEST_URN =
      "urn:core:standard:openurbanapps:dataset:environment:airquality:default";

  @Mock private DataStructureImportService dataStructureImportService;
  @Mock private MappingImportService mappingImportService;
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private DataSourceService dataSourceService;
  @Mock private DataSinkService dataSinkService;
  @Mock private PipelineService pipelineService;
  @Mock private DataSetService dataSetService;
  @Mock private InstallationRecorder installationRecorder;
  @InjectMocks private DataSetImportService importService;

  @Captor private ArgumentCaptor<DataSourceInputDTO> sourceInputCaptor;
  @Captor private ArgumentCaptor<DataSetInputDTO> dataSetInputCaptor;
  @Captor private ArgumentCaptor<List<InstalledArtifact>> artifactLinesCaptor;
  @Captor private ArgumentCaptor<String> catalogEntryIdCaptor;

  private static DataStructureImportInputDTO structureInput() {
    DataStructureImportInputDTO structure = new DataStructureImportInputDTO();
    structure.setName("Air Quality Station");
    structure.setModel(Map.of("$id", STRUCTURE_URN, "$defs", Map.of()));
    return structure;
  }

  private static DataSourceImportInputDTO sourceInput(String structureUrn) {
    DataSourceImportInputDTO source = new DataSourceImportInputDTO();
    source.setName("Station Feed");
    source.setDataStructureUrn(structureUrn);
    return source;
  }

  private static MappingImportInputDTO mappingInput(Map<String, Object> document) {
    MappingImportInputDTO mapping = new MappingImportInputDTO();
    mapping.setName("Station → Observation");
    mapping.setMappingUrn(MAPPING_URN);
    mapping.setDocument(document);
    return mapping;
  }

  /** A mapping document that reads from the bundled structure. */
  private static Map<String, Object> mappingDocument(String sourceUrn) {
    return Map.of("source", sourceUrn, "fields", Map.of("$.result", "$.value"));
  }

  private static DataSetImportInputDTO bundle(
      List<DataStructureImportInputDTO> structures, List<DataSourceImportInputDTO> sources) {
    return bundle(structures, sources, List.of());
  }

  private static DataSetImportInputDTO bundle(
      List<DataStructureImportInputDTO> structures,
      List<DataSourceImportInputDTO> sources,
      List<MappingImportInputDTO> mappings) {
    DataSetImportInputDTO input = new DataSetImportInputDTO();
    input.setName("Air Quality");
    input.setDescription("Bundle description");
    input.setDataStructures(structures);
    input.setDataSources(sources);
    input.setMappings(mappings);
    return input;
  }

  private static DataSinkImportInputDTO sinkInput(String name, String elementUrn) {
    DataSinkImportInputDTO sink = new DataSinkImportInputDTO();
    sink.setName(name);
    sink.setDataSinkType(DataSinkType.POSTGIS);
    // Immutable on purpose: the import must copy before rewriting, never mutate the input.
    sink.setConfiguration(Map.of("tableName", "measurements", "element", elementUrn));
    return sink;
  }

  private static PipelineImportInputDTO pipelineInput(Map<String, Object> model) {
    PipelineImportInputDTO pipeline = new PipelineImportInputDTO();
    pipeline.setName("Zählung zu Messung");
    pipeline.setModel(model);
    return pipeline;
  }

  /** A linear source → mapping → sink graph referencing bundle members by name. */
  private static Map<String, Object> pipelineModel(
      String sourceRef, String mappingRef, String sinkRef) {
    return Map.of(
        "nodes",
        List.of(
            Map.of("id", "n-source", "kind", "source", "sourceRef", sourceRef),
            Map.of("id", "n-mapping", "kind", "mapping", "mappingRef", mappingRef),
            Map.of("id", "n-sink", "kind", "sink", "sinkRef", sinkRef)),
        "edges",
        List.of(
            Map.of("id", "e1", "source", "n-source", "target", "n-mapping"),
            Map.of("id", "e2", "source", "n-mapping", "target", "n-sink")));
  }

  /** A created source as DataSourceService returns it: id, name, minted config URN, DRAFT. */
  private static DataSource createdSource(String name) {
    DataSource source = new DataSource();
    source.setId(UUID.randomUUID());
    source.setName(name);
    source.setConfigurationUrn("urn:core:platform:civitas:datasource:common:feed:x1y2z3:1.0.0");
    source.setDataSourceStatus(DataSourceStatus.DRAFT);
    return source;
  }

  /** A created sink as DataSinkService returns it: id plus minted configuration pins. */
  private static DataSink createdSink() {
    DataSink sink = new DataSink();
    sink.setId(UUID.randomUUID());
    sink.setConfigurationLogicalUrn("urn:core:platform:civitas:datasink:common:table:a1b2c3");
    sink.setConfigurationUrn("urn:core:platform:civitas:datasink:common:table:a1b2c3:1.0.0");
    return sink;
  }

  /** A created pipeline as PipelineService returns it: id plus minted model pin. */
  private static Pipeline createdPipeline() {
    Pipeline pipeline = new Pipeline();
    pipeline.setId(UUID.randomUUID());
    pipeline.setModelLogicalUrn("urn:core:platform:civitas:pipeline:common:flow:p1p2p3");
    return pipeline;
  }

  private static DataSet dataSetWithManifest() {
    DataSet dataSet = new DataSet();
    dataSet.setId(UUID.randomUUID());
    dataSet.setName("Air Quality");
    dataSet.setManifestLogicalUrn(MANIFEST_URN);
    return dataSet;
  }

  private DataStructureVersion version() {
    DataStructure structure = new DataStructure();
    structure.setId(UUID.randomUUID());
    DataStructureVersion version = new DataStructureVersion();
    version.setId(UUID.randomUUID());
    version.setModelUrn(VERSIONED_URN);
    version.setDataStructure(structure);
    return version;
  }

  private void stubUrnHelpers() {
    when(modelRegistryGateway.isDataStructureUrn(STRUCTURE_URN)).thenReturn(true);
    when(modelRegistryGateway.logicalUrn(STRUCTURE_URN)).thenReturn(STRUCTURE_URN);
    // Only the structure path resolves the versioned URN; source-only tests never hit it.
    lenient().when(modelRegistryGateway.logicalUrn(VERSIONED_URN)).thenReturn(STRUCTURE_URN);
  }

  /** Stubs the provenance write to return a saved installation, as the recorder does. */
  private UUID stubInstallationRecord() {
    UUID installationId = UUID.randomUUID();
    Installation saved = new Installation();
    saved.setId(installationId);
    when(installationRecorder.record(any(), any(), any(), any(), any())).thenReturn(saved);
    return installationId;
  }

  /** The artifact lines the orchestrator handed to the recorder. */
  private List<InstalledArtifact> recordedLines() {
    verify(installationRecorder).record(any(), any(), any(), any(), artifactLinesCaptor.capture());
    return artifactLinesCaptor.getValue();
  }

  /** The bundle identity the orchestrator handed to the recorder. */
  private String recordedCatalogEntryId() {
    verify(installationRecorder).record(catalogEntryIdCaptor.capture(), any(), any(), any(), any());
    return catalogEntryIdCaptor.getValue();
  }

  @Test
  void importDataSet_wiresBundledStructureIntoSourceAndCreatesDataSetLast() {
    stubUrnHelpers();
    DataStructureVersion version = version();
    when(dataStructureImportService.importOrReuse(any(DataStructureImportInputDTO.class)))
        .thenReturn(new ImportResolution(version, false));
    DataSource createdSource = new DataSource();
    createdSource.setId(UUID.randomUUID());
    createdSource.setName("Station Feed");
    when(dataSourceService.create(any(DataSourceInputDTO.class))).thenReturn(createdSource);
    DataSet dataSet = new DataSet();
    dataSet.setId(UUID.randomUUID());
    dataSet.setName("Air Quality");
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(dataSet);
    UUID installationId = stubInstallationRecord();

    DataSetImportOutputDTO output =
        importService.importDataSet(
            bundle(List.of(structureInput()), List.of(sourceInput(STRUCTURE_URN))));

    // The bundled structure must be AVAILABLE before the source links to it.
    var order = inOrder(dataStructureImportService, dataSourceService);
    order.verify(dataStructureImportService).ensureAvailable(version);
    order.verify(dataSourceService).create(sourceInputCaptor.capture());
    assertThat(sourceInputCaptor.getValue().getDataStructureVersionId()).isEqualTo(version.getId());
    verify(dataSetService).create(dataSetInputCaptor.capture());
    assertThat(dataSetInputCaptor.getValue().getName()).isEqualTo("Air Quality");

    assertThat(output.getDataSetId()).isEqualTo(dataSet.getId());
    assertThat(output.getInstallationId()).isEqualTo(installationId);
    assertThat(output.getDataStructures()).hasSize(1);
    assertThat(output.getDataStructures().getFirst().getAction())
        .isEqualTo(InstalledArtifactAction.CREATED);
    assertThat(output.getDataSources()).hasSize(1);

    // Provenance is written in the same transaction, one line per touched artifact — the dataset
    // included, and last, because that is when it came into being.
    assertThat(recordedLines())
        .extracting(InstalledArtifact::getArtifactType, InstalledArtifact::getAction)
        .containsExactly(
            tuple(InstalledArtifactType.DATA_STRUCTURE, InstalledArtifactAction.CREATED),
            tuple(InstalledArtifactType.DATA_SOURCE, InstalledArtifactAction.CREATED),
            tuple(InstalledArtifactType.DATA_SET, InstalledArtifactAction.CREATED));
  }

  @Test
  void importDataSet_recordsCatalogEntryIdentityAndReuseInProvenance() {
    // Structure-only bundle: only the versioned-URN resolution is exercised.
    when(modelRegistryGateway.logicalUrn(VERSIONED_URN)).thenReturn(STRUCTURE_URN);
    DataStructureVersion version = version();
    when(dataStructureImportService.importOrReuse(any(DataStructureImportInputDTO.class)))
        .thenReturn(new ImportResolution(version, true));
    DataSet dataSet = new DataSet();
    dataSet.setId(UUID.randomUUID());
    dataSet.setName("Air Quality");
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(dataSet);
    stubInstallationRecord();

    DataSetImportInputDTO input = bundle(List.of(structureInput()), List.of());
    input.setCatalogEntryId("urn:catalog:openurbanapps:usecase:airquality");
    input.setCatalogEntryVersion("1.2.0");
    importService.importDataSet(input);

    assertThat(recordedCatalogEntryId()).isEqualTo("urn:catalog:openurbanapps:usecase:airquality");
    assertThat(recordedLines())
        .extracting(InstalledArtifact::getArtifactType, InstalledArtifact::getAction)
        .containsExactly(
            tuple(InstalledArtifactType.DATA_STRUCTURE, InstalledArtifactAction.REUSED),
            tuple(InstalledArtifactType.DATA_SET, InstalledArtifactAction.CREATED));
  }

  @Test
  void importDataSet_resolvesSourceReferenceAgainstInstalledStructure() {
    stubUrnHelpers();
    DataStructureVersion installed = version();
    when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(STRUCTURE_URN + ":"))
        .thenReturn(Optional.of(installed));
    DataSource createdSource = new DataSource();
    createdSource.setId(UUID.randomUUID());
    createdSource.setName("Station Feed");
    when(dataSourceService.create(any(DataSourceInputDTO.class))).thenReturn(createdSource);
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(new DataSet());
    stubInstallationRecord();

    importService.importDataSet(bundle(List.of(), List.of(sourceInput(STRUCTURE_URN))));

    // Installed-but-never-released structures (single import leaves DRAFT) are released too.
    verify(dataStructureImportService).ensureAvailable(installed);
    verify(dataSourceService).create(sourceInputCaptor.capture());
    assertThat(sourceInputCaptor.getValue().getDataStructureVersionId())
        .isEqualTo(installed.getId());
  }

  @Test
  void importDataSet_whenSourceReferenceUnresolvable_rejectsBeforeCreatingAnything() {
    when(modelRegistryGateway.isDataStructureUrn(STRUCTURE_URN)).thenReturn(true);
    when(modelRegistryGateway.logicalUrn(STRUCTURE_URN)).thenReturn(STRUCTURE_URN);
    when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(STRUCTURE_URN + ":"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                importService.importDataSet(bundle(List.of(), List.of(sourceInput(STRUCTURE_URN)))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("neither part of this bundle nor");

    verify(dataSourceService, never()).create(any());
    verify(dataSetService, never()).create(any());
  }

  @Test
  void importDataSet_whenSourceReferenceIsNoDataStructureUrn_rejects() {
    when(modelRegistryGateway.isDataStructureUrn("not-a-urn")).thenReturn(false);

    assertThatThrownBy(
            () -> importService.importDataSet(bundle(List.of(), List.of(sourceInput("not-a-urn")))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("structure reference must be a CORE URN");

    verify(dataSourceService, never()).create(any());
    verify(dataSetService, never()).create(any());
  }

  @Test
  void importDataSet_storesMappingAfterStructuresAndLinksItIntoTheManifestAfterTheDataSet() {
    when(modelRegistryGateway.logicalUrn(VERSIONED_URN)).thenReturn(STRUCTURE_URN);
    when(modelRegistryGateway.isDataStructureUrn(STRUCTURE_URN)).thenReturn(true);
    when(modelRegistryGateway.logicalUrn(STRUCTURE_URN)).thenReturn(STRUCTURE_URN);
    DataStructureVersion version = version();
    when(dataStructureImportService.importOrReuse(any(DataStructureImportInputDTO.class)))
        .thenReturn(new ImportResolution(version, false));
    when(mappingImportService.importOrReuse(any(MappingImportInputDTO.class)))
        .thenReturn(new MappingResolution(MAPPING_URN, MAPPING_VERSIONED_URN, false));
    DataSet dataSet = dataSetWithManifest();
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(dataSet);
    stubInstallationRecord();

    DataSetImportOutputDTO output =
        importService.importDataSet(
            bundle(
                List.of(structureInput()),
                List.of(),
                List.of(mappingInput(mappingDocument(STRUCTURE_URN)))));

    // A mapping may only be stored once the structures it names exist, and it can only be linked
    // once the dataset's manifest does.
    var order =
        inOrder(
            dataStructureImportService, mappingImportService, dataSetService, modelRegistryGateway);
    order.verify(dataStructureImportService).importOrReuse(any());
    order.verify(mappingImportService).importOrReuse(any());
    order.verify(dataSetService).create(any());
    order.verify(modelRegistryGateway).linkToDataSet(MANIFEST_URN, MAPPING_URN);

    assertThat(output.getMappings()).hasSize(1);
    assertThat(output.getMappings().getFirst().getUrn()).isEqualTo(MAPPING_URN);
    assertThat(output.getMappings().getFirst().getId()).isNull();
    assertThat(output.getMappings().getFirst().getAction())
        .isEqualTo(InstalledArtifactAction.CREATED);
  }

  /**
   * The provenance rule for a registry-only artifact: a urn (it has registry identity) and no
   * shellId (it has no host row) — the mirror image of a data source.
   */
  @Test
  void importDataSet_recordsMappingLineWithUrnAndWithoutShellId() {
    when(modelRegistryGateway.isDataStructureUrn(STRUCTURE_URN)).thenReturn(true);
    when(modelRegistryGateway.logicalUrn(STRUCTURE_URN)).thenReturn(STRUCTURE_URN);
    when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(STRUCTURE_URN + ":"))
        .thenReturn(Optional.of(version()));
    when(mappingImportService.importOrReuse(any(MappingImportInputDTO.class)))
        .thenReturn(new MappingResolution(MAPPING_URN, MAPPING_VERSIONED_URN, false));
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(new DataSet());
    stubInstallationRecord();

    importService.importDataSet(
        bundle(List.of(), List.of(), List.of(mappingInput(mappingDocument(STRUCTURE_URN)))));

    assertThat(recordedLines())
        .filteredOn(line -> line.getArtifactType() == InstalledArtifactType.MAPPING)
        .singleElement()
        .satisfies(
            artifact -> {
              assertThat(artifact.getUrn()).isEqualTo(MAPPING_URN);
              assertThat(artifact.getShellId()).isNull();
              assertThat(artifact.getAction()).isEqualTo(InstalledArtifactAction.CREATED);
            });
  }

  @Test
  void importDataSet_whenMappingIdentityAlreadyInstalled_recordsItAsReused() {
    when(mappingImportService.importOrReuse(any(MappingImportInputDTO.class)))
        .thenReturn(new MappingResolution(MAPPING_URN, MAPPING_VERSIONED_URN, true));
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(new DataSet());
    stubInstallationRecord();

    DataSetImportOutputDTO output =
        importService.importDataSet(
            bundle(List.of(), List.of(), List.of(mappingInput(Map.of("fields", Map.of())))));

    assertThat(output.getMappings().getFirst().getAction())
        .isEqualTo(InstalledArtifactAction.REUSED);
    assertThat(recordedLines())
        .filteredOn(line -> line.getArtifactType() == InstalledArtifactType.MAPPING)
        .singleElement()
        .satisfies(
            artifact -> assertThat(artifact.getAction()).isEqualTo(InstalledArtifactAction.REUSED));
  }

  /** source/target are optional in mapping.schema.json, so the guard must be conditional. */
  @Test
  void importDataSet_whenMappingHasNoStructureReferences_isAccepted() {
    when(mappingImportService.importOrReuse(any(MappingImportInputDTO.class)))
        .thenReturn(new MappingResolution(MAPPING_URN, MAPPING_VERSIONED_URN, false));
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(new DataSet());
    stubInstallationRecord();

    importService.importDataSet(
        bundle(List.of(), List.of(), List.of(mappingInput(Map.of("fields", Map.of())))));

    verify(modelRegistryGateway, never()).isDataStructureUrn(any());
    verify(mappingImportService).importOrReuse(any());
  }

  /** A structure shipped by the same bundle resolves from the bundle map — no database lookup. */
  @Test
  void importDataSet_whenMappingReferencesBundledStructure_resolvesWithoutTouchingTheRepository() {
    when(modelRegistryGateway.logicalUrn(VERSIONED_URN)).thenReturn(STRUCTURE_URN);
    when(modelRegistryGateway.isDataStructureUrn(STRUCTURE_URN)).thenReturn(true);
    when(modelRegistryGateway.logicalUrn(STRUCTURE_URN)).thenReturn(STRUCTURE_URN);
    when(dataStructureImportService.importOrReuse(any(DataStructureImportInputDTO.class)))
        .thenReturn(new ImportResolution(version(), false));
    when(mappingImportService.importOrReuse(any(MappingImportInputDTO.class)))
        .thenReturn(new MappingResolution(MAPPING_URN, MAPPING_VERSIONED_URN, false));
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(new DataSet());
    stubInstallationRecord();

    importService.importDataSet(
        bundle(
            List.of(structureInput()),
            List.of(),
            List.of(mappingInput(mappingDocument(STRUCTURE_URN)))));

    verify(dataStructureVersionRepository, never()).findFirstByModelUrnStartingWith(any());
  }

  @Test
  void importDataSet_whenMappingStructureReferenceUnresolvable_rejectsBeforeCreatingAnything() {
    when(modelRegistryGateway.isDataStructureUrn(STRUCTURE_URN)).thenReturn(true);
    when(modelRegistryGateway.logicalUrn(STRUCTURE_URN)).thenReturn(STRUCTURE_URN);
    when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(STRUCTURE_URN + ":"))
        .thenReturn(Optional.empty());

    DataSetImportInputDTO input =
        bundle(List.of(), List.of(), List.of(mappingInput(mappingDocument(STRUCTURE_URN))));

    assertThatThrownBy(() -> importService.importDataSet(input))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("neither part of this bundle nor installed");

    verify(mappingImportService, never()).importOrReuse(any());
    verify(dataSetService, never()).create(any());
    verify(installationRecorder, never()).record(any(), any(), any(), any(), any());
  }

  @Test
  void importDataSet_whenMappingStructureReferenceIsNoDataStructureUrn_rejects() {
    when(modelRegistryGateway.isDataStructureUrn(MAPPING_URN)).thenReturn(false);

    // A mapping URN where a structure URN belongs.
    DataSetImportInputDTO input =
        bundle(List.of(), List.of(), List.of(mappingInput(mappingDocument(MAPPING_URN))));

    assertThatThrownBy(() -> importService.importDataSet(input))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("source must be a CORE URN of artifact type 'datastructure'");

    verify(mappingImportService, never()).importOrReuse(any());
    verify(dataSetService, never()).create(any());
  }

  @Test
  void importDataSet_rewritesSinkElementToTheResolvedVersionedUrn() {
    stubUrnHelpers();
    when(dataStructureImportService.importOrReuse(any(DataStructureImportInputDTO.class)))
        .thenReturn(new ImportResolution(version(), false));
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(dataSetWithManifest());
    DataSink sink = createdSink();
    when(dataSinkService.create(any(DataSinkInputDTO.class))).thenReturn(sink);
    stubInstallationRecord();

    DataSetImportInputDTO input = bundle(List.of(structureInput()), List.of());
    // The bundle authors the target structure's LOGICAL urn — it cannot know which version the
    // receiving instance resolves.
    input.setDataSinks(List.of(sinkInput("PostGIS Tabelle", STRUCTURE_URN)));
    DataSetImportOutputDTO output = importService.importDataSet(input);

    ArgumentCaptor<DataSinkInputDTO> sinkCaptor = ArgumentCaptor.forClass(DataSinkInputDTO.class);
    verify(dataSinkService).create(sinkCaptor.capture());
    assertThat(sinkCaptor.getValue().getConfiguration().get("element")).isEqualTo(VERSIONED_URN);
    assertThat(sinkCaptor.getValue().getDataSetId()).isNotNull();

    assertThat(output.getDataSinks())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.getId()).isEqualTo(sink.getId());
              assertThat(result.getUrn()).isEqualTo(sink.getConfigurationLogicalUrn());
              assertThat(result.getAction()).isEqualTo(InstalledArtifactAction.CREATED);
            });
  }

  @Test
  void importDataSet_wiresPipelineGraphAndDerivesLinksFromNameReferences() {
    stubUrnHelpers();
    when(dataStructureImportService.importOrReuse(any(DataStructureImportInputDTO.class)))
        .thenReturn(new ImportResolution(version(), false));
    DataSource source = createdSource("Station Feed");
    when(dataSourceService.create(any(DataSourceInputDTO.class))).thenReturn(source);
    when(mappingImportService.importOrReuse(any(MappingImportInputDTO.class)))
        .thenReturn(new MappingResolution(MAPPING_URN, MAPPING_VERSIONED_URN, false));
    DataSet dataSet = dataSetWithManifest();
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(dataSet);
    DataSink sink = createdSink();
    when(dataSinkService.create(any(DataSinkInputDTO.class))).thenReturn(sink);
    Pipeline pipeline = createdPipeline();
    when(pipelineService.create(any(PipelineInputDTO.class))).thenReturn(pipeline);
    stubInstallationRecord();

    DataSetImportInputDTO input =
        bundle(
            List.of(structureInput()),
            List.of(sourceInput(STRUCTURE_URN)),
            List.of(mappingInput(mappingDocument(STRUCTURE_URN))));
    input.setDataSinks(List.of(sinkInput("PostGIS Tabelle", STRUCTURE_URN)));
    input.setPipelines(
        List.of(
            pipelineInput(
                pipelineModel("Station Feed", "Station → Observation", "PostGIS Tabelle"))));
    DataSetImportOutputDTO output = importService.importDataSet(input);

    // The graph's name references are rewritten to the minted URNs of the created artifacts…
    ArgumentCaptor<PipelineInputDTO> pipelineCaptor =
        ArgumentCaptor.forClass(PipelineInputDTO.class);
    verify(pipelineService).create(pipelineCaptor.capture());
    PipelineInputDTO created = pipelineCaptor.getValue();
    assertThat(created.getDataSetId()).isEqualTo(dataSet.getId());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> nodes = (List<Map<String, Object>>) created.getModel().get("nodes");
    assertThat(nodes.get(0)).containsEntry("sourceRef", source.getConfigurationUrn());
    // Pinned, not logical: a logical reference silently means "current version".
    assertThat(nodes.get(1)).containsEntry("mappingRef", MAPPING_VERSIONED_URN);
    assertThat(nodes.get(2)).containsEntry("sinkRef", sink.getConfigurationUrn());

    // …the source/sink links are derived from exactly those resolutions…
    assertThat(created.getDataSourceIds()).containsExactly(source.getId());
    assertThat(created.getDataSinkIds()).containsExactly(sink.getId());

    // …and the DRAFT bundle source is released first, because pipelines only link AVAILABLE ones.
    verify(dataSourceService).release(source.getId());

    // Provenance lines in touch order; sink and pipeline always CREATED.
    assertThat(recordedLines())
        .extracting(InstalledArtifact::getArtifactType)
        .containsExactly(
            InstalledArtifactType.DATA_STRUCTURE,
            InstalledArtifactType.DATA_SOURCE,
            InstalledArtifactType.MAPPING,
            InstalledArtifactType.DATA_SET,
            InstalledArtifactType.DATA_SINK,
            InstalledArtifactType.PIPELINE);
    assertThat(output.getPipelines())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.getId()).isEqualTo(pipeline.getId());
              assertThat(result.getUrn()).isEqualTo(pipeline.getModelLogicalUrn());
              assertThat(result.getAction()).isEqualTo(InstalledArtifactAction.CREATED);
            });
  }

  @Test
  void importDataSet_passesLiteralUrnReferencesThroughWithoutLinking() {
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(dataSetWithManifest());
    Pipeline pipeline = createdPipeline();
    when(pipelineService.create(any(PipelineInputDTO.class))).thenReturn(pipeline);
    stubInstallationRecord();

    String literalRef = "urn:core:platform:civitas:datasource:common:existing:q9w8e7:2.0.0";
    DataSetImportInputDTO input = bundle(List.of(), List.of());
    input.setPipelines(
        List.of(
            pipelineInput(
                Map.of(
                    "nodes",
                    List.of(Map.of("id", "n1", "kind", "source", "sourceRef", literalRef))))));
    importService.importDataSet(input);

    ArgumentCaptor<PipelineInputDTO> pipelineCaptor =
        ArgumentCaptor.forClass(PipelineInputDTO.class);
    verify(pipelineService).create(pipelineCaptor.capture());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> nodes =
        (List<Map<String, Object>>) pipelineCaptor.getValue().getModel().get("nodes");
    assertThat(nodes.get(0)).containsEntry("sourceRef", literalRef);
    // A literal URN derives no link and releases nothing — the artifact is expected to exist.
    assertThat(pipelineCaptor.getValue().getDataSourceIds()).isNull();
    verify(dataSourceService, never()).release(any());
  }

  @Test
  void importDataSet_whenPipelineReferencesUnknownName_rejects() {
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(dataSetWithManifest());

    DataSetImportInputDTO input = bundle(List.of(), List.of());
    input.setPipelines(
        List.of(
            pipelineInput(
                Map.of(
                    "nodes",
                    List.of(
                        Map.of(
                            "id",
                            "n1",
                            "kind",
                            "datasource",
                            "sourceRef",
                            "Unbekannte Quelle"))))));

    assertThatThrownBy(() -> importService.importDataSet(input))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("sourceRef references 'Unbekannte Quelle'");

    verify(pipelineService, never()).create(any());
    verify(installationRecorder, never()).record(any(), any(), any(), any(), any());
  }

  /**
   * A config-less source resolves by name but has no minted configuration URN a graph could point
   * at. This must be its own message — "not part of this bundle" would send the author hunting for
   * a typo that does not exist. Found live: the 1.1.0 catalogue source shipped without connector
   * configuration.
   */
  @Test
  void importDataSet_whenPipelineReferencesConfiglessSource_saysSoInsteadOfNameNotFound() {
    stubUrnHelpers();
    when(dataStructureImportService.importOrReuse(any(DataStructureImportInputDTO.class)))
        .thenReturn(new ImportResolution(version(), false));
    DataSource configless = createdSource("Zählstellen-Feed");
    configless.setConfigurationUrn(null);
    when(dataSourceService.create(any(DataSourceInputDTO.class))).thenReturn(configless);
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(dataSetWithManifest());

    DataSetImportInputDTO input =
        bundle(List.of(structureInput()), List.of(sourceInput(STRUCTURE_URN)));
    input.setPipelines(
        List.of(
            pipelineInput(
                Map.of(
                    "nodes",
                    List.of(
                        Map.of("id", "n1", "kind", "source", "sourceRef", "Zählstellen-Feed"))))));

    assertThatThrownBy(() -> importService.importDataSet(input))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("without connector configuration");

    verify(pipelineService, never()).create(any());
    verify(installationRecorder, never()).record(any(), any(), any(), any(), any());
  }

  @Test
  void importDataSet_whenSinkNamesCollide_rejects() {
    stubUrnHelpers();
    when(dataStructureImportService.importOrReuse(any(DataStructureImportInputDTO.class)))
        .thenReturn(new ImportResolution(version(), false));
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(dataSetWithManifest());
    when(dataSinkService.create(any(DataSinkInputDTO.class))).thenReturn(createdSink());

    DataSetImportInputDTO input = bundle(List.of(structureInput()), List.of());
    input.setDataSinks(
        List.of(
            sinkInput("PostGIS Tabelle", STRUCTURE_URN),
            sinkInput("PostGIS Tabelle", STRUCTURE_URN)));

    assertThatThrownBy(() -> importService.importDataSet(input))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("duplicate data sink name");

    verify(installationRecorder, never()).record(any(), any(), any(), any(), any());
  }
}
