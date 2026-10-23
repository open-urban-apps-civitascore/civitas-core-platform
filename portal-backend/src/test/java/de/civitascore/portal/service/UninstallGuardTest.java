package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.SagaInFlightException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link UninstallGuard}: which artifacts of an installation stop an uninstall, and
 * which references do not count.
 */
@ExtendWith(MockitoExtension.class)
class UninstallGuardTest {

  private static final UUID INSTALLATION_ID =
      UUID.fromString("10101010-1010-4000-8000-000000000001");
  private static final UUID STRUCTURE_ID = UUID.fromString("20202020-2020-4000-8000-000000000002");
  private static final UUID VERSION_ID = UUID.fromString("30303030-3030-4000-8000-000000000003");
  private static final UUID SOURCE_ID = UUID.fromString("40404040-4040-4000-8000-000000000004");
  private static final UUID DATASET_ID = UUID.fromString("50505050-5050-4000-8000-000000000005");
  private static final UUID OTHER_DATASET_ID =
      UUID.fromString("60606060-6060-4000-8000-000000000006");
  private static final UUID OTHER_SOURCE_ID =
      UUID.fromString("70707070-7070-4000-8000-000000000007");

  private static final String STRUCTURE_URN =
      "urn:core:platform:civitas:datastructure:common:20202020-2020-4000-8000-000000000002";
  private static final String VERSIONED_STRUCTURE_URN = STRUCTURE_URN + ":1.0.0";
  private static final String ELEMENT_URN =
      "urn:core:platform:civitas:element:common:20202020-2020-4000-8000-000000000002";
  private static final String SOURCE_URN =
      "urn:core:platform:civitas:datasource:common:40404040-4040-4000-8000-000000000004";
  private static final String MANIFEST_URN =
      "urn:core:platform:civitas:dataset:common:50505050-5050-4000-8000-000000000005";
  private static final String MAPPING_URN =
      "urn:core:platform:civitas:mapping:common:80808080-8080-4000-8000-000000000008";
  private static final String OTHER_MAPPING_URN =
      "urn:core:platform:civitas:mapping:common:90909090-9090-4000-8000-000000000009";

  /** A version token as the registry adds it to a reference. */
  private static final String VERSION = ":1.0.0";

  @Mock private DataSetService dataSetService;
  @Mock private DataSourceService dataSourceService;
  @Mock private DataStructureService dataStructureService;
  @Mock private PipelineRepository pipelineRepository;
  @Mock private DataSourceRepository dataSourceRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;

  @InjectMocks private UninstallGuard guard;

  /** The registry removes the version token, and an artifact in these tests has no host model. */
  @BeforeEach
  void registryWithoutHosts() {
    lenient()
        .when(modelRegistryGateway.logicalUrn(anyString()))
        .thenAnswer(invocation -> invocation.<String>getArgument(0).replace(VERSION, ""));
    lenient().when(modelRegistryGateway.hostModelUrnsOf(anyString())).thenReturn(Set.of());
  }

  @Test
  void requireRemovable_whenEachArtifactIsADraftThatOnlyTheInstallationUses_passes() {
    dataSetIs(dataSet(DataSetStatus.DRAFT));
    dataSourceIs(DataSourceStatus.DRAFT);
    pipelinesOfTheDataSourceAreIn(DATASET_ID);
    dataStructureIsPinnedBy(SOURCE_ID);
    when(modelRegistryGateway.referencesTo(VERSIONED_STRUCTURE_URN))
        .thenReturn(List.of(SOURCE_URN + VERSION, MAPPING_URN + VERSION));

    assertThatCode(() -> guard.requireRemovable(INSTALLATION_ID, treeMap()))
        .doesNotThrowAnyException();
  }

  @Test
  void requireRemovable_whenAnElementOfTheInstalledStructureReferencesIt_passes() {
    dataSetIs(dataSet(DataSetStatus.DRAFT));
    dataSourceIs(DataSourceStatus.DRAFT);
    pipelinesOfTheDataSourceAreIn(DATASET_ID);
    dataStructureIsPinnedBy(SOURCE_ID);
    when(modelRegistryGateway.referencesTo(VERSIONED_STRUCTURE_URN))
        .thenReturn(List.of(ELEMENT_URN + VERSION));
    when(modelRegistryGateway.hostModelUrnsOf(ELEMENT_URN + VERSION))
        .thenReturn(Set.of(STRUCTURE_URN, ELEMENT_URN));

    assertThatCode(() -> guard.requireRemovable(INSTALLATION_ID, treeMap()))
        .doesNotThrowAnyException();
  }

  @Test
  void requireRemovable_whenNoArtifactExistsAnyMore_passes() {
    when(dataSetService.findById(DATASET_ID)).thenReturn(Optional.empty());
    when(dataSourceService.findById(SOURCE_ID)).thenReturn(Optional.empty());
    when(dataStructureService.findById(STRUCTURE_ID)).thenReturn(Optional.empty());

    assertThatCode(() -> guard.requireRemovable(INSTALLATION_ID, treeMap()))
        .doesNotThrowAnyException();

    verifyNoInteractions(pipelineRepository, dataSourceRepository);
  }

  @Test
  void requireRemovable_whenDataSetIsReleased_throwsResourceInUse() {
    dataSetIs(dataSet(DataSetStatus.AVAILABLE));

    assertThatThrownBy(() -> guard.requireRemovable(INSTALLATION_ID, treeMap()))
        .isInstanceOf(ResourceInUseException.class)
        .hasMessageContaining("is released")
        .hasMessageContaining("Unrelease the Dataset");
  }

  @Test
  void requireRemovable_whenDataSetHasInfrastructure_throwsResourceInUse() {
    DataSet dataSet = dataSet(DataSetStatus.DRAFT);
    dataSet.setProvisioned(true);
    dataSetIs(dataSet);

    assertThatThrownBy(() -> guard.requireRemovable(INSTALLATION_ID, treeMap()))
        .isInstanceOf(ResourceInUseException.class)
        .hasMessageContaining("has infrastructure");
  }

  @Test
  void requireRemovable_whenOperationOnDataSetIsInProgress_throwsSagaInFlight() {
    DataSet dataSet = dataSet(DataSetStatus.READY);
    dataSet.setPendingSagaType(PendingSagaType.UNRELEASE);
    dataSetIs(dataSet);

    assertThatThrownBy(() -> guard.requireRemovable(INSTALLATION_ID, treeMap()))
        .isInstanceOf(SagaInFlightException.class);
  }

  @Test
  void requireRemovable_whenDataSourceIsReleased_throwsResourceInUse() {
    dataSetIs(dataSet(DataSetStatus.DRAFT));
    dataSourceIs(DataSourceStatus.AVAILABLE);

    assertThatThrownBy(() -> guard.requireRemovable(INSTALLATION_ID, treeMap()))
        .isInstanceOf(ResourceInUseException.class)
        .hasMessageContaining("Unrelease the Data source");
  }

  @Test
  void requireRemovable_whenPipelineOfAnotherDataSetUsesTheDataSource_throwsResourceInUse() {
    dataSetIs(dataSet(DataSetStatus.DRAFT));
    dataSourceIs(DataSourceStatus.DRAFT);
    pipelinesOfTheDataSourceAreIn(DATASET_ID, OTHER_DATASET_ID);

    assertThatThrownBy(() -> guard.requireRemovable(INSTALLATION_ID, treeMap()))
        .isInstanceOf(ResourceInUseException.class)
        .hasMessageContaining("used by a Pipeline that the installation did not create");
  }

  @Test
  void requireRemovable_whenAnotherDataSourcePinsTheStructure_throwsResourceInUse() {
    dataSetIs(dataSet(DataSetStatus.DRAFT));
    dataSourceIs(DataSourceStatus.DRAFT);
    pipelinesOfTheDataSourceAreIn(DATASET_ID);
    dataStructureIsPinnedBy(SOURCE_ID, OTHER_SOURCE_ID);

    assertThatThrownBy(() -> guard.requireRemovable(INSTALLATION_ID, treeMap()))
        .isInstanceOf(ResourceInUseException.class)
        .hasMessageContaining("used by a Data source that the installation did not create");
  }

  @Test
  void requireRemovable_whenAnotherArtifactReferencesTheStructure_throwsResourceInUse() {
    dataSetIs(dataSet(DataSetStatus.DRAFT));
    dataSourceIs(DataSourceStatus.DRAFT);
    pipelinesOfTheDataSourceAreIn(DATASET_ID);
    dataStructureIsPinnedBy(SOURCE_ID);
    when(modelRegistryGateway.referencesTo(VERSIONED_STRUCTURE_URN))
        .thenReturn(List.of(MAPPING_URN + VERSION, OTHER_MAPPING_URN + VERSION));

    assertThatThrownBy(() -> guard.requireRemovable(INSTALLATION_ID, treeMap()))
        .isInstanceOf(ResourceInUseException.class)
        .hasMessageContaining("used by an artifact that the installation did not create");
  }

  private void dataSetIs(DataSet dataSet) {
    when(dataSetService.findById(DATASET_ID)).thenReturn(Optional.of(dataSet));
  }

  private void dataSourceIs(DataSourceStatus status) {
    DataSource dataSource = new DataSource();
    dataSource.setId(SOURCE_ID);
    dataSource.setName("Cadastre database");
    dataSource.setDataSourceStatus(status);
    when(dataSourceService.findById(SOURCE_ID)).thenReturn(Optional.of(dataSource));
  }

  private void pipelinesOfTheDataSourceAreIn(UUID... dataSetIds) {
    List<Pipeline> pipelines =
        List.of(dataSetIds).stream()
            .map(
                dataSetId -> {
                  DataSet dataSet = new DataSet();
                  dataSet.setId(dataSetId);
                  Pipeline pipeline = new Pipeline();
                  pipeline.setId(UUID.randomUUID());
                  pipeline.setDataSet(dataSet);
                  return pipeline;
                })
            .toList();
    when(pipelineRepository.findByDataSourcesId(SOURCE_ID)).thenReturn(pipelines);
  }

  /** The structure exists with one version, and these DRAFT Data sources pin the version. */
  private void dataStructureIsPinnedBy(UUID... dataSourceIds) {
    DataStructureVersion version = new DataStructureVersion();
    version.setId(VERSION_ID);
    version.setModelUrn(VERSIONED_STRUCTURE_URN);
    DataStructure dataStructure = new DataStructure();
    dataStructure.setId(STRUCTURE_ID);
    dataStructure.setName("Street tree");
    dataStructure.getDataStructureVersions().add(version);
    when(dataStructureService.findById(STRUCTURE_ID)).thenReturn(Optional.of(dataStructure));
    when(dataSourceRepository.findIdsByDataStructureVersionIdInAndStatus(any(), any()))
        .thenAnswer(
            invocation ->
                invocation.getArgument(1) == DataSourceStatus.DRAFT
                    ? List.of(dataSourceIds)
                    : List.of());
  }

  private static DataSet dataSet(DataSetStatus status) {
    DataSet dataSet = new DataSet();
    dataSet.setId(DATASET_ID);
    dataSet.setName("Street trees");
    dataSet.setDataSetStatus(status);
    return dataSet;
  }

  /** The lines of a tree map installation, with the URNs that the install recorded. */
  private static List<InstalledArtifact> treeMap() {
    return List.of(
        line(InstalledArtifactType.DATA_STRUCTURE, STRUCTURE_ID, STRUCTURE_URN),
        line(InstalledArtifactType.DATA_SOURCE, SOURCE_ID, SOURCE_URN),
        line(InstalledArtifactType.DATA_SET, DATASET_ID, MANIFEST_URN),
        line(InstalledArtifactType.MAPPING, null, MAPPING_URN));
  }

  private static InstalledArtifact line(InstalledArtifactType type, UUID shellId, String urn) {
    InstalledArtifact line = new InstalledArtifact();
    line.setArtifactType(type);
    line.setShellId(shellId);
    line.setUrn(urn);
    return line;
  }
}
