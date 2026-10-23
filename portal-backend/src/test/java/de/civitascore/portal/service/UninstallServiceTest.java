package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link UninstallService}. The services of the platform and the guard are mocks, so
 * these tests do not show the rules of the platform. {@code InstallationControllerIntegrationTest}
 * shows them.
 */
@ExtendWith(MockitoExtension.class)
class UninstallServiceTest {

  private static final UUID INSTALLATION_ID =
      UUID.fromString("10101010-1010-4000-8000-000000000001");
  private static final UUID ROW_ID = UUID.fromString("20202020-2020-4000-8000-000000000002");
  private static final UUID TREE_ID = UUID.fromString("30303030-3030-4000-8000-000000000003");
  private static final UUID SOURCE_ID = UUID.fromString("40404040-4040-4000-8000-000000000004");
  private static final UUID DATASET_ID = UUID.fromString("50505050-5050-4000-8000-000000000005");
  private static final UUID API_ID = UUID.fromString("60606060-6060-4000-8000-000000000006");
  private static final UUID SINK_ID = UUID.fromString("70707070-7070-4000-8000-000000000007");
  private static final UUID PIPELINE_ID = UUID.fromString("80808080-8080-4000-8000-000000000008");
  private static final UUID STYLE_ID = UUID.fromString("90909090-9090-4000-8000-000000000009");
  private static final UUID LAYER_ID = UUID.fromString("a0a0a0a0-a0a0-4000-8000-00000000000a");

  @Mock private InstallationRepository installationRepository;
  @Mock private DataSetService dataSetService;
  @Mock private DataSourceService dataSourceService;
  @Mock private DataStructureService dataStructureService;
  @Mock private UninstallGuard uninstallGuard;

  @InjectMocks private UninstallService uninstallService;

  @Test
  void uninstall_whenInstallationIsUnknown_throwsResourceNotFound() {
    when(installationRepository.findById(INSTALLATION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> uninstallService.uninstall(INSTALLATION_ID))
        .isInstanceOf(ResourceNotFoundException.class);

    verifyNoInteractions(uninstallGuard, dataSetService, dataSourceService, dataStructureService);
  }

  @Test
  void uninstall_whenInstallationIsUninstalled_changesNothing() {
    Installation installation = treeMap();
    LocalDateTime uninstalledAt = LocalDateTime.of(2026, 10, 23, 9, 0);
    installation.setUninstalledAt(uninstalledAt);
    when(installationRepository.findById(INSTALLATION_ID)).thenReturn(Optional.of(installation));

    uninstallService.uninstall(INSTALLATION_ID);

    verifyNoInteractions(uninstallGuard, dataSetService, dataSourceService, dataStructureService);
    verify(installationRepository, never()).save(any());
    assertThat(installation.getUninstalledAt()).isEqualTo(uninstalledAt);
  }

  @Test
  void uninstall_whenArtifactsExist_removesThemInTheReverseOrderOfTheInstall() {
    Installation installation = treeMap();
    when(installationRepository.findById(INSTALLATION_ID)).thenReturn(Optional.of(installation));
    allArtifactsExist();

    uninstallService.uninstall(INSTALLATION_ID);

    InOrder order =
        inOrder(
            uninstallGuard,
            dataSetService,
            dataSourceService,
            dataStructureService,
            installationRepository);
    order.verify(uninstallGuard).requireRemovable(INSTALLATION_ID, installation.getArtifacts());
    order.verify(dataSetService).deleteById(DATASET_ID);
    order.verify(dataSourceService).deleteById(SOURCE_ID);
    order.verify(dataStructureService).deleteById(TREE_ID);
    order.verify(dataStructureService).deleteById(ROW_ID);
    order.verify(installationRepository).save(installation);
    assertThat(installation.isUninstalled()).isTrue();
  }

  @Test
  void uninstall_whenGuardRefuses_removesNothing() {
    Installation installation = treeMap();
    when(installationRepository.findById(INSTALLATION_ID)).thenReturn(Optional.of(installation));
    doThrow(new ResourceInUseException("Installation", INSTALLATION_ID, "Dataset is released"))
        .when(uninstallGuard)
        .requireRemovable(any(), any());

    assertThatThrownBy(() -> uninstallService.uninstall(INSTALLATION_ID))
        .isInstanceOf(ResourceInUseException.class);

    verify(dataSetService, never()).deleteById(any());
    verify(dataSourceService, never()).deleteById(any());
    verify(dataStructureService, never()).deleteById(any());
    verify(installationRepository, never()).save(any());
    assertThat(installation.isUninstalled()).isFalse();
  }

  @Test
  void uninstall_whenDataSetNoLongerExists_skipsItAndRemovesTheRest() {
    Installation installation = treeMap();
    when(installationRepository.findById(INSTALLATION_ID)).thenReturn(Optional.of(installation));
    when(dataSetService.existsById(DATASET_ID)).thenReturn(false);
    when(dataSourceService.existsById(SOURCE_ID)).thenReturn(true);
    when(dataStructureService.existsById(any())).thenReturn(true);

    uninstallService.uninstall(INSTALLATION_ID);

    verify(dataSetService, never()).deleteById(any());
    verify(dataSourceService).deleteById(SOURCE_ID);
    verify(dataStructureService).deleteById(ROW_ID);
    verify(dataStructureService).deleteById(TREE_ID);
    verify(installationRepository).save(installation);
  }

  @Test
  void uninstall_whenServiceRefusesARemoval_namesTheArtifact() {
    Installation installation = treeMap();
    when(installationRepository.findById(INSTALLATION_ID)).thenReturn(Optional.of(installation));
    allArtifactsExist();
    // Lenient: the uninstall removes the street tree first, and only the cadastre row refuses.
    lenient()
        .doThrow(new ResourceInUseException("DataStructure", ROW_ID, "A version is referenced."))
        .when(dataStructureService)
        .deleteById(ROW_ID);

    assertThatThrownBy(() -> uninstallService.uninstall(INSTALLATION_ID))
        .isInstanceOf(ResourceInUseException.class)
        .hasMessageContaining("'Cadastre row' is in use")
        .hasMessageContaining("A version is referenced.");

    verify(installationRepository, never()).save(any());
  }

  private void allArtifactsExist() {
    when(dataSetService.existsById(DATASET_ID)).thenReturn(true);
    when(dataSourceService.existsById(SOURCE_ID)).thenReturn(true);
    when(dataStructureService.existsById(any())).thenReturn(true);
  }

  /** The record of a tree map package, with one line for each artifact in the install order. */
  private static Installation treeMap() {
    Installation installation = new Installation();
    installation.setId(INSTALLATION_ID);
    installation.setPackageId("tree-map");
    installation.setPackageVersion("1.0.0");
    installation.addArtifact(line(InstalledArtifactType.DATA_STRUCTURE, "Cadastre row", ROW_ID));
    installation.addArtifact(line(InstalledArtifactType.DATA_STRUCTURE, "Street tree", TREE_ID));
    installation.addArtifact(line(InstalledArtifactType.DATA_SOURCE, "Cadastre", SOURCE_ID));
    installation.addArtifact(line(InstalledArtifactType.DATA_SET, "Street trees", DATASET_ID));
    installation.addArtifact(line(InstalledArtifactType.NAMED_API, "Tree map", API_ID));
    installation.addArtifact(line(InstalledArtifactType.MAPPING, "Row to tree", null));
    installation.addArtifact(line(InstalledArtifactType.DATA_SINK, "Tree table", SINK_ID));
    installation.addArtifact(line(InstalledArtifactType.PIPELINE, "Tree import", PIPELINE_ID));
    installation.addArtifact(line(InstalledArtifactType.STYLE, "trees-green", STYLE_ID));
    installation.addArtifact(line(InstalledArtifactType.LAYER, "street_trees", LAYER_ID));
    return installation;
  }

  private static InstalledArtifact line(InstalledArtifactType type, String name, UUID shellId) {
    InstalledArtifact line = new InstalledArtifact();
    line.setArtifactType(type);
    line.setName(name);
    line.setShellId(shellId);
    return line;
  }
}
