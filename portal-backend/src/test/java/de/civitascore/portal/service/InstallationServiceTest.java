package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.input.InstallationInputDTO;
import de.civitascore.portal.model.input.PackageManifestInputDTO;
import de.civitascore.portal.model.input.PackageMemberInputDTO;
import de.civitascore.portal.model.input.PackageMemberKind;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.repository.PublishedStructureRepository;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for the uninstall of {@link InstallationService}: the order of the removals, what is
 * skipped, and that a refusal leaves the installation active. The services of the artifacts and the
 * guard are mocked, thus these tests do not prove the rules of the platform. The API tests do.
 */
@ExtendWith(MockitoExtension.class)
class InstallationServiceTest {

  private static final String PACKAGE_ID = "urn:core:package:openurbanapps:soil_moisture";
  private static final UUID INSTALLATION_ID =
      UUID.fromString("11111111-1111-4000-8000-000000000001");
  private static final UUID DATA_SET_ID = UUID.fromString("22222222-2222-4000-8000-000000000002");
  private static final UUID DATA_SOURCE_ID =
      UUID.fromString("33333333-3333-4000-8000-000000000003");
  private static final UUID DATA_STRUCTURE_ID =
      UUID.fromString("44444444-4444-4000-8000-000000000004");
  private static final UUID DATA_SINK_ID = UUID.fromString("55555555-5555-4000-8000-000000000005");
  private static final UUID PIPELINE_ID = UUID.fromString("66666666-6666-4000-8000-000000000006");

  @Mock private InstallationRepository installationRepository;
  @Mock private DataStructureService dataStructureService;
  @Mock private DataStructureVersionService dataStructureVersionService;
  @Mock private DataSourceService dataSourceService;
  @Mock private DataSetService dataSetService;
  @Mock private MappingService mappingService;
  @Mock private DataSinkService dataSinkService;
  @Mock private PipelineService pipelineService;
  @Mock private PublishedStructureRepository publishedStructureRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private UninstallGuard uninstallGuard;

  @InjectMocks private InstallationService installationService;

  @Captor private ArgumentCaptor<List<InstalledArtifact>> linesCaptor;

  private Installation installation;

  @BeforeEach
  void createInstallation() {
    installation = new Installation();
    installation.setId(INSTALLATION_ID);
    installation.setPackageId(PACKAGE_ID);
    // The order of the install: the kinds that others reference come first.
    installation.addArtifact(
        line(InstalledArtifactType.DATA_STRUCTURE, "Soil moisture", DATA_STRUCTURE_ID));
    installation.addArtifact(
        line(InstalledArtifactType.DATA_SOURCE, "Soil sensor", DATA_SOURCE_ID));
    installation.addArtifact(
        line(InstalledArtifactType.DATA_SET, "Soil moisture readings", DATA_SET_ID));
    installation.addArtifact(line(InstalledArtifactType.MAPPING, "Soil to table", null));
    installation.addArtifact(line(InstalledArtifactType.DATA_SINK, "Soil table", DATA_SINK_ID));
    installation.addArtifact(line(InstalledArtifactType.PIPELINE, "Soil ingest", PIPELINE_ID));
    lenient()
        .when(installationRepository.findById(INSTALLATION_ID))
        .thenReturn(Optional.of(installation));
  }

  private static InstalledArtifact line(InstalledArtifactType type, String name, UUID shellId) {
    InstalledArtifact line = new InstalledArtifact();
    line.setArtifactType(type);
    line.setName(name);
    line.setShellId(shellId);
    line.setAction(InstalledArtifactAction.CREATED);
    return line;
  }

  private static DataSource dataSource(DataSourceStatus status) {
    DataSource dataSource = new DataSource();
    dataSource.setId(DATA_SOURCE_ID);
    dataSource.setDataSourceStatus(status);
    return dataSource;
  }

  private void stubArtifactsExist(DataSourceStatus dataSourceStatus) {
    when(dataSetService.existsById(DATA_SET_ID)).thenReturn(true);
    when(dataSourceService.findById(DATA_SOURCE_ID))
        .thenReturn(Optional.of(dataSource(dataSourceStatus)));
    when(dataStructureService.existsById(DATA_STRUCTURE_ID)).thenReturn(true);
  }

  @Test
  void uninstall_whenArtifactsExist_removesDataSetThenDataSourceThenDataStructure() {
    stubArtifactsExist(DataSourceStatus.AVAILABLE);

    installationService.uninstall(INSTALLATION_ID);

    InOrder order =
        inOrder(uninstallGuard, dataSetService, dataSourceService, dataStructureService);
    order.verify(uninstallGuard).requireRemovable(eq(INSTALLATION_ID), any());
    order.verify(dataSetService).deleteById(DATA_SET_ID);
    order.verify(dataSourceService).unrelease(DATA_SOURCE_ID);
    order.verify(dataSourceService).deleteById(DATA_SOURCE_ID);
    order.verify(dataStructureService).deleteById(DATA_STRUCTURE_ID);
  }

  @Test
  void uninstall_whenArtifactsExist_leavesMappingSinkAndPipelineToTheDataSet() {
    stubArtifactsExist(DataSourceStatus.AVAILABLE);

    installationService.uninstall(INSTALLATION_ID);

    verifyNoInteractions(mappingService, dataSinkService, pipelineService);
  }

  @Test
  void uninstall_whenArtifactsAreRemoved_keepsTheRecordWithTheTimeOfTheUninstall() {
    stubArtifactsExist(DataSourceStatus.AVAILABLE);

    installationService.uninstall(INSTALLATION_ID);

    assertThat(installation.getUninstalledAt()).isNotNull();
    assertThat(installation.isUninstalled()).isTrue();
    assertThat(installation.getArtifacts()).hasSize(6);
    verify(installationRepository).save(installation);
    verify(installationRepository, never()).delete(any());
  }

  @Test
  void uninstall_whenDataSourceIsDraft_deletesItWithoutUnrelease() {
    stubArtifactsExist(DataSourceStatus.DRAFT);

    installationService.uninstall(INSTALLATION_ID);

    verify(dataSourceService, never()).unrelease(any());
    verify(dataSourceService).deleteById(DATA_SOURCE_ID);
  }

  @Test
  void uninstall_whenArtifactsNoLongerExist_skipsThemAndCompletes() {
    when(dataSetService.existsById(DATA_SET_ID)).thenReturn(false);
    when(dataSourceService.findById(DATA_SOURCE_ID)).thenReturn(Optional.empty());
    when(dataStructureService.existsById(DATA_STRUCTURE_ID)).thenReturn(false);

    installationService.uninstall(INSTALLATION_ID);

    verify(dataSetService, never()).deleteById(any());
    verify(dataSourceService, never()).deleteById(any());
    verify(dataStructureService, never()).deleteById(any());
    assertThat(installation.isUninstalled()).isTrue();
  }

  @Test
  void uninstall_whenArtifactWasReused_doesNotRemoveIt() {
    installation.getArtifacts().getFirst().setAction(InstalledArtifactAction.REUSED);
    when(dataSetService.existsById(DATA_SET_ID)).thenReturn(true);
    when(dataSourceService.findById(DATA_SOURCE_ID))
        .thenReturn(Optional.of(dataSource(DataSourceStatus.AVAILABLE)));

    installationService.uninstall(INSTALLATION_ID);

    verify(uninstallGuard).requireRemovable(eq(INSTALLATION_ID), linesCaptor.capture());
    assertThat(linesCaptor.getValue())
        .extracting(InstalledArtifact::getArtifactType)
        .doesNotContain(InstalledArtifactType.DATA_STRUCTURE);
    verifyNoInteractions(dataStructureService);
  }

  @Test
  void uninstall_whenGuardRefuses_removesNothingAndStaysActive() {
    doThrow(new ResourceInUseException("Installation", INSTALLATION_ID, "Dataset is released"))
        .when(uninstallGuard)
        .requireRemovable(eq(INSTALLATION_ID), any());

    assertThatThrownBy(() -> installationService.uninstall(INSTALLATION_ID))
        .isInstanceOf(ResourceInUseException.class);

    verifyNoInteractions(dataSetService, dataSourceService, dataStructureService);
    assertThat(installation.isUninstalled()).isFalse();
    verify(installationRepository, never()).save(any());
  }

  @Test
  void uninstall_whenServiceRefusesARemoval_namesTheArtifactAndStaysActive() {
    stubArtifactsExist(DataSourceStatus.AVAILABLE);
    doThrow(
            new ResourceInUseException(
                "DataStructure",
                DATA_STRUCTURE_ID,
                "Cannot modify DataStructure because one or more of its versions is still"
                    + " referenced.",
                List.of("urn:core:platform:civitas:mapping:common:Other:eee5555555")))
        .when(dataStructureService)
        .deleteById(DATA_STRUCTURE_ID);

    assertThatThrownBy(() -> installationService.uninstall(INSTALLATION_ID))
        .isInstanceOfSatisfying(
            ResourceInUseException.class,
            refusal -> {
              assertThat(refusal.getMessage())
                  .contains("'Soil moisture' is in use")
                  .contains("one or more of its versions is still referenced");
              assertThat(refusal.getResourceId()).isEqualTo(DATA_STRUCTURE_ID);
              assertThat(refusal.getBlockedBy())
                  .containsExactly("urn:core:platform:civitas:mapping:common:Other:eee5555555");
            });

    assertThat(installation.isUninstalled()).isFalse();
    verify(installationRepository, never()).save(any());
  }

  @Test
  void uninstall_whenAlreadyUninstalled_changesNothing() {
    LocalDateTime firstUninstall = LocalDateTime.of(2026, 9, 1, 12, 0);
    installation.setUninstalledAt(firstUninstall);

    installationService.uninstall(INSTALLATION_ID);

    assertThat(installation.getUninstalledAt()).isEqualTo(firstUninstall);
    verifyNoInteractions(uninstallGuard, dataSetService, dataSourceService, dataStructureService);
    verify(installationRepository, never()).save(any());
  }

  @Test
  void uninstall_whenInstallationDoesNotExist_throwsNotFound() {
    UUID unknownId = UUID.fromString("99999999-9999-4000-8000-000000000009");
    when(installationRepository.findById(unknownId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> installationService.uninstall(unknownId))
        .isInstanceOf(ResourceNotFoundException.class);

    verifyNoInteractions(uninstallGuard);
  }

  @Test
  void install_whenPackageHasAnActiveInstallation_refuses() {
    PackageManifestInputDTO manifest = new PackageManifestInputDTO();
    manifest.setId(PACKAGE_ID);
    InstallationInputDTO input = new InstallationInputDTO();
    input.setPackageManifest(manifest);
    when(installationRepository.existsByPackageIdAndUninstalledAtIsNull(PACKAGE_ID))
        .thenReturn(true);

    assertThatThrownBy(() -> installationService.install(input))
        .isInstanceOf(UniqueConstraintViolationException.class)
        .hasMessageContaining("is already installed");

    verify(installationRepository, never()).save(any());
    verify(installationRepository, never()).saveAndFlush(any());
  }

  @Test
  void install_whenAnInstallOfTheSamePackageCommitsFirst_refusesBeforeItCreatesAnything() {
    PackageMemberInputDTO member = new PackageMemberInputDTO();
    member.setKind(PackageMemberKind.DATASTRUCTURE);
    member.setUrn("urn:core:standard:openurbanapps:datastructure:environment:soil_moisture:demo");
    member.setName("Soil moisture");
    member.setDescription("Installed by the test");
    member.setContent(Map.of("title", "Soil moisture"));
    PackageManifestInputDTO manifest = new PackageManifestInputDTO();
    manifest.setId(PACKAGE_ID);
    manifest.setMembers(List.of(member));
    InstallationInputDTO input = new InstallationInputDTO();
    input.setPackageManifest(manifest);
    // The check passes: the other install has not committed yet. The unique index refuses the row.
    when(installationRepository.existsByPackageIdAndUninstalledAtIsNull(PACKAGE_ID))
        .thenReturn(false);
    when(installationRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("uq_installations_active_package"));

    assertThatThrownBy(() -> installationService.install(input))
        .isInstanceOf(UniqueConstraintViolationException.class)
        .hasMessageContaining("is already installed");

    verifyNoInteractions(dataStructureService, dataStructureVersionService, modelRegistryGateway);
  }
}
