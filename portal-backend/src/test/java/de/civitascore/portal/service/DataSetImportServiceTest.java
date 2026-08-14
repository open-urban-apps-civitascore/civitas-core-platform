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
import de.civitascore.portal.model.output.DataSetImportOutputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.BundleInstallationRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.service.DataStructureImportService.ImportResolution;
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
 * Unit tests for {@link DataSetImportService}: orchestration order (structures → sources →
 * dataset), URN-based structure resolution for sources (bundle first, then installed), and the
 * explicit rejection of not-yet-supported bundle parts. The collaborating services are mocked;
 * their own guards are covered in {@link DataStructureImportServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class DataSetImportServiceTest {

  private static final String STRUCTURE_URN =
      "urn:core:city:openurbanapps:datastructure:environment:airqualitystation:default";
  private static final String VERSIONED_URN = STRUCTURE_URN + ":1.0.0";

  @Mock private DataStructureImportService dataStructureImportService;
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private DataSourceService dataSourceService;
  @Mock private DataSetService dataSetService;
  @Mock private BundleInstallationRepository bundleInstallationRepository;
  @InjectMocks private DataSetImportService importService;

  @Captor private ArgumentCaptor<DataSourceInputDTO> sourceInputCaptor;
  @Captor private ArgumentCaptor<DataSetInputDTO> dataSetInputCaptor;
  @Captor private ArgumentCaptor<BundleInstallation> installationCaptor;

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

  private static DataSetImportInputDTO bundle(
      List<DataStructureImportInputDTO> structures, List<DataSourceImportInputDTO> sources) {
    DataSetImportInputDTO input = new DataSetImportInputDTO();
    input.setName("Air Quality");
    input.setDescription("Bundle description");
    input.setDataStructures(structures);
    input.setDataSources(sources);
    return input;
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

  /** Stubs the provenance save to assign an id, as JPA does on persist. */
  private UUID stubInstallationSave() {
    UUID installationId = UUID.randomUUID();
    when(bundleInstallationRepository.save(any(BundleInstallation.class)))
        .thenAnswer(
            invocation -> {
              BundleInstallation saved = invocation.getArgument(0);
              saved.setId(installationId);
              return saved;
            });
    return installationId;
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
    UUID installationId = stubInstallationSave();

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

    // Provenance is written in the same transaction, one line per touched artifact.
    verify(bundleInstallationRepository).save(installationCaptor.capture());
    BundleInstallation recorded = installationCaptor.getValue();
    assertThat(recorded.getDataSetId()).isEqualTo(dataSet.getId());
    assertThat(recorded.getArtifacts())
        .extracting(InstalledArtifact::getArtifactType, InstalledArtifact::getAction)
        .containsExactlyInAnyOrder(
            tuple(InstalledArtifactType.DATA_STRUCTURE, InstalledArtifactAction.CREATED),
            tuple(InstalledArtifactType.DATA_SOURCE, InstalledArtifactAction.CREATED));
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
    stubInstallationSave();

    DataSetImportInputDTO input = bundle(List.of(structureInput()), List.of());
    input.setBundleUrn("urn:catalog:openurbanapps:usecase:airquality");
    input.setBundleVersion("1.2.0");
    importService.importDataSet(input);

    verify(bundleInstallationRepository).save(installationCaptor.capture());
    BundleInstallation recorded = installationCaptor.getValue();
    assertThat(recorded.getBundleUrn()).isEqualTo("urn:catalog:openurbanapps:usecase:airquality");
    assertThat(recorded.getBundleVersion()).isEqualTo("1.2.0");
    assertThat(recorded.getArtifacts())
        .singleElement()
        .satisfies(
            artifact -> assertThat(artifact.getAction()).isEqualTo(InstalledArtifactAction.REUSED));
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
    stubInstallationSave();

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
  void importDataSet_withUnsupportedParts_rejectsWithClearMessage() {
    DataSetImportInputDTO input = bundle(List.of(), List.of());
    input.setPipelines(List.of(Map.of("name", "pipeline")));

    assertThatThrownBy(() -> importService.importDataSet(input))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("not yet supported");

    verify(dataStructureImportService, never()).importOrReuse(any());
    verify(dataSetService, never()).create(any());
    verify(bundleInstallationRepository, never()).save(any());
  }
}
