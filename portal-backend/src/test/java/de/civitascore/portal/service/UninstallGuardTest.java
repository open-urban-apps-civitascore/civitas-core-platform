package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.InstalledArtifactAction;
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
 * Unit tests for {@link UninstallGuard}: which states and which references stop an uninstall, and
 * that the references between the artifacts of the installation do not. The services, the
 * repositories and the model registry are mocked.
 */
@ExtendWith(MockitoExtension.class)
class UninstallGuardTest {

  private static final UUID INSTALLATION_ID =
      UUID.fromString("11111111-1111-4000-8000-000000000001");
  private static final UUID DATA_SET_ID = UUID.fromString("22222222-2222-4000-8000-000000000002");
  private static final UUID DATA_SOURCE_ID =
      UUID.fromString("33333333-3333-4000-8000-000000000003");
  private static final UUID DATA_STRUCTURE_ID =
      UUID.fromString("44444444-4444-4000-8000-000000000004");
  private static final UUID VERSION_ID = UUID.fromString("55555555-5555-4000-8000-000000000005");
  private static final UUID OTHER_DATA_SET_ID =
      UUID.fromString("66666666-6666-4000-8000-000000000006");
  private static final UUID OTHER_DATA_SOURCE_ID =
      UUID.fromString("77777777-7777-4000-8000-000000000007");
  private static final UUID OTHER_PIPELINE_ID =
      UUID.fromString("88888888-8888-4000-8000-000000000008");

  private static final String STRUCTURE_URN =
      "urn:core:platform:civitas:datastructure:common:SoilMoisture:aaa1111111";
  private static final String SOURCE_URN =
      "urn:core:platform:civitas:datasource:common:SoilSensor:bbb2222222";
  private static final String DATA_SET_URN =
      "urn:core:platform:civitas:dataset:common:SoilMoisture:ccc3333333";
  private static final String MAPPING_URN =
      "urn:core:platform:civitas:mapping:common:SoilToTable:ddd4444444";
  private static final String OTHER_MAPPING_URN =
      "urn:core:platform:civitas:mapping:common:Other:eee5555555";
  private static final String ELEMENT_URN =
      "urn:core:platform:civitas:element:common:Reading:fff6666666";
  private static final String VERSION = ":1.0.0";

  @Mock private DataSetService dataSetService;
  @Mock private DataSourceService dataSourceService;
  @Mock private DataStructureService dataStructureService;
  @Mock private PipelineRepository pipelineRepository;
  @Mock private DataSourceRepository dataSourceRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;

  @InjectMocks private UninstallGuard uninstallGuard;

  private DataSet dataSet;
  private DataSource dataSource;
  private DataStructure dataStructure;
  private List<InstalledArtifact> lines;

  @BeforeEach
  void createInstalledArtifacts() {
    dataSet = new DataSet();
    dataSet.setId(DATA_SET_ID);
    dataSet.setName("Soil moisture readings");

    dataSource = new DataSource();
    dataSource.setId(DATA_SOURCE_ID);
    dataSource.setName("Soil sensor");
    dataSource.setDataSourceStatus(DataSourceStatus.AVAILABLE);

    dataStructure = new DataStructure();
    dataStructure.setId(DATA_STRUCTURE_ID);
    dataStructure.setName("Soil moisture");
    DataStructureVersion version = new DataStructureVersion();
    version.setId(VERSION_ID);
    version.setModelUrn(STRUCTURE_URN + VERSION);
    version.setDataStructure(dataStructure);
    dataStructure.getDataStructureVersions().add(version);

    lines =
        List.of(
            line(InstalledArtifactType.DATA_STRUCTURE, DATA_STRUCTURE_ID, STRUCTURE_URN),
            line(InstalledArtifactType.DATA_SOURCE, DATA_SOURCE_ID, SOURCE_URN),
            line(InstalledArtifactType.DATA_SET, DATA_SET_ID, DATA_SET_URN),
            line(InstalledArtifactType.MAPPING, null, MAPPING_URN));

    lenient().when(dataSetService.findById(DATA_SET_ID)).thenReturn(Optional.of(dataSet));
    lenient().when(dataSourceService.findById(DATA_SOURCE_ID)).thenReturn(Optional.of(dataSource));
    lenient()
        .when(dataStructureService.findById(DATA_STRUCTURE_ID))
        .thenReturn(Optional.of(dataStructure));
    // Mirrors UrnParser: a logical URN is the versioned one without its trailing version segment.
    lenient()
        .when(modelRegistryGateway.logicalUrn(anyString()))
        .thenAnswer(
            invocation -> {
              String urn = invocation.getArgument(0);
              return urn.matches(".*:\\d+\\.\\d+\\.\\d+$")
                  ? urn.substring(0, urn.lastIndexOf(':'))
                  : urn;
            });
  }

  private static InstalledArtifact line(InstalledArtifactType type, UUID shellId, String urn) {
    InstalledArtifact line = new InstalledArtifact();
    line.setArtifactType(type);
    line.setShellId(shellId);
    line.setUrn(urn);
    line.setAction(InstalledArtifactAction.CREATED);
    return line;
  }

  private static Pipeline pipelineOf(UUID pipelineId, UUID dataSetId) {
    DataSet owner = new DataSet();
    owner.setId(dataSetId);
    Pipeline pipeline = new Pipeline();
    pipeline.setId(pipelineId);
    pipeline.setDataSet(owner);
    return pipeline;
  }

  /** The guard asks one time for each status, thus each status gets an answer. */
  private void stubDataSourcesPinnedToTheVersion(List<UUID> draft, List<UUID> available) {
    when(dataSourceRepository.findIdsByDataStructureVersionIdInAndStatus(
            Set.of(VERSION_ID), DataSourceStatus.DRAFT))
        .thenReturn(draft);
    when(dataSourceRepository.findIdsByDataStructureVersionIdInAndStatus(
            Set.of(VERSION_ID), DataSourceStatus.AVAILABLE))
        .thenReturn(available);
  }

  @Test
  void requireRemovable_whenOnlyTheInstallationUsesItsArtifacts_passes() {
    when(pipelineRepository.findByDataSourcesId(DATA_SOURCE_ID))
        .thenReturn(List.of(pipelineOf(UUID.randomUUID(), DATA_SET_ID)));
    stubDataSourcesPinnedToTheVersion(List.of(), List.of(DATA_SOURCE_ID));
    when(modelRegistryGateway.referencesTo(STRUCTURE_URN + VERSION))
        .thenReturn(List.of(SOURCE_URN + VERSION, MAPPING_URN + VERSION));

    assertThatCode(() -> uninstallGuard.requireRemovable(INSTALLATION_ID, lines))
        .doesNotThrowAnyException();
  }

  @Test
  void requireRemovable_whenDataSetIsStaged_passes() {
    dataSet.setDataSetStatus(DataSetStatus.READY);

    assertThatCode(() -> uninstallGuard.requireRemovable(INSTALLATION_ID, lines))
        .doesNotThrowAnyException();
  }

  @Test
  void requireRemovable_whenDataSetIsReleased_refuses() {
    dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
    dataSet.setProvisioned(true);

    assertThatThrownBy(() -> uninstallGuard.requireRemovable(INSTALLATION_ID, lines))
        .isInstanceOf(ResourceInUseException.class)
        .hasMessageContaining("Dataset 'Soil moisture readings' is released")
        .hasMessageContaining("Unrelease the Dataset and delete it");
  }

  @Test
  void requireRemovable_whenDataSetHasInfrastructure_refuses() {
    dataSet.setDataSetStatus(DataSetStatus.READY);
    dataSet.setProvisioned(true);

    assertThatThrownBy(() -> uninstallGuard.requireRemovable(INSTALLATION_ID, lines))
        .isInstanceOf(ResourceInUseException.class)
        .hasMessageContaining("has infrastructure on the platform")
        .hasMessageContaining("Delete the Dataset");
  }

  @Test
  void requireRemovable_whenOperationOnDataSetIsInProgress_refuses() {
    dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
    dataSet.setPendingSagaType(PendingSagaType.CREATE);

    assertThatThrownBy(() -> uninstallGuard.requireRemovable(INSTALLATION_ID, lines))
        .isInstanceOf(SagaInFlightException.class)
        .hasMessageContaining("is in progress");
  }

  @Test
  void requireRemovable_whenPipelineOfAnotherDataSetUsesTheDataSource_refuses() {
    when(pipelineRepository.findByDataSourcesId(DATA_SOURCE_ID))
        .thenReturn(
            List.of(
                pipelineOf(UUID.randomUUID(), DATA_SET_ID),
                pipelineOf(OTHER_PIPELINE_ID, OTHER_DATA_SET_ID)));

    assertThatThrownBy(() -> uninstallGuard.requireRemovable(INSTALLATION_ID, lines))
        .isInstanceOfSatisfying(
            ResourceInUseException.class,
            refusal -> {
              assertThat(refusal.getResourceId()).isEqualTo(DATA_SOURCE_ID);
              assertThat(refusal.getBlockedBy()).containsExactly(OTHER_PIPELINE_ID.toString());
              // The answer names the artifact of the installation and not what uses it.
              assertThat(refusal.getMessage())
                  .contains("Data source 'Soil sensor'")
                  .doesNotContain(OTHER_PIPELINE_ID.toString());
            });
  }

  @Test
  void requireRemovable_whenAnotherDataSourcePinsTheDataStructure_refuses() {
    stubDataSourcesPinnedToTheVersion(List.of(OTHER_DATA_SOURCE_ID), List.of(DATA_SOURCE_ID));

    assertThatThrownBy(() -> uninstallGuard.requireRemovable(INSTALLATION_ID, lines))
        .isInstanceOfSatisfying(
            ResourceInUseException.class,
            refusal -> {
              assertThat(refusal.getResourceId()).isEqualTo(DATA_STRUCTURE_ID);
              assertThat(refusal.getBlockedBy()).containsExactly(OTHER_DATA_SOURCE_ID.toString());
              assertThat(refusal.getMessage()).contains("Data structure 'Soil moisture'");
            });
  }

  @Test
  void requireRemovable_whenArtifactOutsideTheInstallationReferencesTheDataStructure_refuses() {
    when(modelRegistryGateway.referencesTo(STRUCTURE_URN + VERSION))
        .thenReturn(List.of(MAPPING_URN + VERSION, OTHER_MAPPING_URN + VERSION));

    assertThatThrownBy(() -> uninstallGuard.requireRemovable(INSTALLATION_ID, lines))
        .isInstanceOfSatisfying(
            ResourceInUseException.class,
            refusal -> {
              assertThat(refusal.getResourceId()).isEqualTo(DATA_STRUCTURE_ID);
              assertThat(refusal.getBlockedBy()).containsExactly(OTHER_MAPPING_URN + VERSION);
              assertThat(refusal.getMessage()).doesNotContain(OTHER_MAPPING_URN);
            });
  }

  @Test
  void requireRemovable_whenElementOfTheInstallationReferencesTheDataStructure_passes() {
    when(modelRegistryGateway.referencesTo(STRUCTURE_URN + VERSION))
        .thenReturn(List.of(ELEMENT_URN + VERSION));
    when(modelRegistryGateway.hostModelUrnsOf(ELEMENT_URN + VERSION))
        .thenReturn(Set.of(STRUCTURE_URN));

    assertThatCode(() -> uninstallGuard.requireRemovable(INSTALLATION_ID, lines))
        .doesNotThrowAnyException();
  }

  @Test
  void requireRemovable_whenArtifactNoLongerExists_doesNotCheckIt() {
    when(dataSetService.findById(DATA_SET_ID)).thenReturn(Optional.empty());
    when(dataSourceService.findById(DATA_SOURCE_ID)).thenReturn(Optional.empty());
    when(dataStructureService.findById(DATA_STRUCTURE_ID)).thenReturn(Optional.empty());

    assertThatCode(() -> uninstallGuard.requireRemovable(INSTALLATION_ID, lines))
        .doesNotThrowAnyException();

    verify(pipelineRepository, never()).findByDataSourcesId(any());
    verify(modelRegistryGateway, never()).referencesTo(any());
  }
}
