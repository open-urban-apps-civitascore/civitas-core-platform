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

import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.BundleInstallation;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.input.DataSetImportInputDTO;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSourceImportInputDTO;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataStructureImportInputDTO;
import de.civitascore.portal.model.input.MappingImportInputDTO;
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
 * → dataset → manifest links), URN-based structure resolution for sources and mappings (bundle
 * first, then installed), and the explicit rejection of not-yet-supported bundle parts. The
 * collaborating services are mocked; their own guards are covered in {@link
 * DataStructureImportServiceTest} and {@link MappingImportServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class DataSetImportServiceTest {

  private static final String STRUCTURE_URN =
      "urn:core:city:openurbanapps:datastructure:environment:airqualitystation:default";
  private static final String VERSIONED_URN = STRUCTURE_URN + ":1.0.0";
  private static final String MAPPING_URN =
      "urn:core:city:openurbanapps:mapping:environment:stationtoobservation:default";
  private static final String MANIFEST_URN =
      "urn:core:city:openurbanapps:dataset:environment:airquality:default";

  @Mock private DataStructureImportService dataStructureImportService;
  @Mock private MappingImportService mappingImportService;
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private DataSourceService dataSourceService;
  @Mock private DataSetService dataSetService;
  @Mock private InstallationRecorder installationRecorder;
  @InjectMocks private DataSetImportService importService;

  @Captor private ArgumentCaptor<DataSourceInputDTO> sourceInputCaptor;
  @Captor private ArgumentCaptor<DataSetInputDTO> dataSetInputCaptor;
  @Captor private ArgumentCaptor<List<InstalledArtifact>> artifactLinesCaptor;
  @Captor private ArgumentCaptor<String> bundleIdCaptor;

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
    BundleInstallation saved = new BundleInstallation();
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
  private String recordedBundleId() {
    verify(installationRecorder).record(bundleIdCaptor.capture(), any(), any(), any(), any());
    return bundleIdCaptor.getValue();
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
  void importDataSet_recordsBundleIdentityAndReuseInProvenance() {
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
    input.setBundleId("urn:catalog:openurbanapps:usecase:airquality");
    input.setBundleVersion("1.2.0");
    importService.importDataSet(input);

    assertThat(recordedBundleId()).isEqualTo("urn:catalog:openurbanapps:usecase:airquality");
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
        .hasMessageContaining("dataStructureUrn");

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
        .thenReturn(new MappingResolution(MAPPING_URN, false));
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
        .thenReturn(new MappingResolution(MAPPING_URN, false));
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
        .thenReturn(new MappingResolution(MAPPING_URN, true));
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
        .thenReturn(new MappingResolution(MAPPING_URN, false));
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
        .thenReturn(new MappingResolution(MAPPING_URN, false));
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
  void importDataSet_withUnsupportedParts_rejectsWithClearMessage() {
    DataSetImportInputDTO input = bundle(List.of(), List.of());
    input.setPipelines(List.of(Map.of("name", "pipeline")));

    assertThatThrownBy(() -> importService.importDataSet(input))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("not yet supported");

    verify(dataStructureImportService, never()).importOrReuse(any());
    verify(dataSetService, never()).create(any());
    verify(installationRecorder, never()).record(any(), any(), any(), any(), any());
  }
}
