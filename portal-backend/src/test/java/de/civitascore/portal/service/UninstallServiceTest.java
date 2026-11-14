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

import de.civitascore.portal.model.embedded.UseCaseArtifactType;
import de.civitascore.portal.model.entity.UseCase;
import de.civitascore.portal.model.entity.UseCaseArtifact;
import de.civitascore.portal.repository.UseCaseRepository;
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
 * these tests do not show the rules of the platform. {@code UseCaseControllerIntegrationTest} shows
 * them.
 */
@ExtendWith(MockitoExtension.class)
class UninstallServiceTest {

  private static final UUID USE_CASE_ID = UUID.fromString("10101010-1010-4000-8000-000000000001");
  private static final UUID ROW_ID = UUID.fromString("20202020-2020-4000-8000-000000000002");
  private static final UUID TREE_ID = UUID.fromString("30303030-3030-4000-8000-000000000003");
  private static final UUID SOURCE_ID = UUID.fromString("40404040-4040-4000-8000-000000000004");
  private static final UUID DATASET_ID = UUID.fromString("50505050-5050-4000-8000-000000000005");
  private static final UUID API_ID = UUID.fromString("60606060-6060-4000-8000-000000000006");
  private static final UUID SINK_ID = UUID.fromString("70707070-7070-4000-8000-000000000007");
  private static final UUID PIPELINE_ID = UUID.fromString("80808080-8080-4000-8000-000000000008");
  private static final UUID STYLE_ID = UUID.fromString("90909090-9090-4000-8000-000000000009");
  private static final UUID LAYER_ID = UUID.fromString("a0a0a0a0-a0a0-4000-8000-00000000000a");

  @Mock private UseCaseRepository useCaseRepository;
  @Mock private DataSetService dataSetService;
  @Mock private DataSourceService dataSourceService;
  @Mock private DataStructureService dataStructureService;
  @Mock private UninstallGuard uninstallGuard;

  @InjectMocks private UninstallService uninstallService;

  @Test
  void uninstall_whenUseCaseIsUnknown_throwsResourceNotFound() {
    when(useCaseRepository.findById(USE_CASE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> uninstallService.uninstall(USE_CASE_ID))
        .isInstanceOf(ResourceNotFoundException.class);

    verifyNoInteractions(uninstallGuard, dataSetService, dataSourceService, dataStructureService);
  }

  @Test
  void uninstallInDatapool_whenUseCaseIsInAnotherDatapool_throwsResourceNotFound() {
    UUID datapoolId = UUID.fromString("b0b0b0b0-b0b0-4000-8000-00000000000b");
    when(useCaseRepository.findByIdAndDatapoolId(USE_CASE_ID, datapoolId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> uninstallService.uninstallInDatapool(datapoolId, USE_CASE_ID))
        .isInstanceOf(ResourceNotFoundException.class);

    verifyNoInteractions(uninstallGuard, dataSetService, dataSourceService, dataStructureService);
  }

  @Test
  void uninstallInDatapool_whenUseCaseIsInTheDatapool_removesItsArtifacts() {
    UUID datapoolId = UUID.fromString("b0b0b0b0-b0b0-4000-8000-00000000000b");
    UseCase useCase = treeMap();
    when(useCaseRepository.findByIdAndDatapoolId(USE_CASE_ID, datapoolId))
        .thenReturn(Optional.of(useCase));
    allArtifactsExist();

    uninstallService.uninstallInDatapool(datapoolId, USE_CASE_ID);

    verify(uninstallGuard).requireRemovable(USE_CASE_ID, useCase.getArtifacts());
    verify(dataSetService).deleteById(DATASET_ID);
    assertThat(useCase.isUninstalled()).isTrue();
  }

  @Test
  void uninstall_whenUseCaseIsUninstalled_changesNothing() {
    UseCase useCase = treeMap();
    LocalDateTime uninstalledAt = LocalDateTime.of(2026, 10, 23, 9, 0);
    useCase.setUninstalledAt(uninstalledAt);
    when(useCaseRepository.findById(USE_CASE_ID)).thenReturn(Optional.of(useCase));

    uninstallService.uninstall(USE_CASE_ID);

    verifyNoInteractions(uninstallGuard, dataSetService, dataSourceService, dataStructureService);
    verify(useCaseRepository, never()).save(any());
    assertThat(useCase.getUninstalledAt()).isEqualTo(uninstalledAt);
  }

  @Test
  void uninstall_whenArtifactsExist_removesThemInTheReverseOrderOfTheInstall() {
    UseCase useCase = treeMap();
    when(useCaseRepository.findById(USE_CASE_ID)).thenReturn(Optional.of(useCase));
    allArtifactsExist();

    uninstallService.uninstall(USE_CASE_ID);

    InOrder order =
        inOrder(
            uninstallGuard,
            dataSetService,
            dataSourceService,
            dataStructureService,
            useCaseRepository);
    order.verify(uninstallGuard).requireRemovable(USE_CASE_ID, useCase.getArtifacts());
    order.verify(dataSetService).deleteById(DATASET_ID);
    order.verify(dataSourceService).deleteById(SOURCE_ID);
    order.verify(dataStructureService).deleteById(TREE_ID);
    order.verify(dataStructureService).deleteById(ROW_ID);
    order.verify(useCaseRepository).save(useCase);
    assertThat(useCase.isUninstalled()).isTrue();
  }

  @Test
  void uninstall_whenGuardRefuses_removesNothing() {
    UseCase useCase = treeMap();
    when(useCaseRepository.findById(USE_CASE_ID)).thenReturn(Optional.of(useCase));
    doThrow(new ResourceInUseException("UseCase", USE_CASE_ID, "Dataset is released"))
        .when(uninstallGuard)
        .requireRemovable(any(), any());

    assertThatThrownBy(() -> uninstallService.uninstall(USE_CASE_ID))
        .isInstanceOf(ResourceInUseException.class);

    verify(dataSetService, never()).deleteById(any());
    verify(dataSourceService, never()).deleteById(any());
    verify(dataStructureService, never()).deleteById(any());
    verify(useCaseRepository, never()).save(any());
    assertThat(useCase.isUninstalled()).isFalse();
  }

  @Test
  void uninstall_whenDataSetNoLongerExists_skipsItAndRemovesTheRest() {
    UseCase useCase = treeMap();
    when(useCaseRepository.findById(USE_CASE_ID)).thenReturn(Optional.of(useCase));
    when(dataSetService.existsById(DATASET_ID)).thenReturn(false);
    when(dataSourceService.existsById(SOURCE_ID)).thenReturn(true);
    when(dataStructureService.existsById(any())).thenReturn(true);

    uninstallService.uninstall(USE_CASE_ID);

    verify(dataSetService, never()).deleteById(any());
    verify(dataSourceService).deleteById(SOURCE_ID);
    verify(dataStructureService).deleteById(ROW_ID);
    verify(dataStructureService).deleteById(TREE_ID);
    verify(useCaseRepository).save(useCase);
  }

  @Test
  void uninstall_whenServiceRefusesARemoval_namesTheArtifact() {
    UseCase useCase = treeMap();
    when(useCaseRepository.findById(USE_CASE_ID)).thenReturn(Optional.of(useCase));
    allArtifactsExist();
    // Lenient: the uninstall removes the street tree first, and only the cadastre row refuses.
    lenient()
        .doThrow(new ResourceInUseException("DataStructure", ROW_ID, "A version is referenced."))
        .when(dataStructureService)
        .deleteById(ROW_ID);

    assertThatThrownBy(() -> uninstallService.uninstall(USE_CASE_ID))
        .isInstanceOf(ResourceInUseException.class)
        .hasMessageContaining("'Cadastre row' is in use")
        .hasMessageContaining("A version is referenced.");

    verify(useCaseRepository, never()).save(any());
  }

  private void allArtifactsExist() {
    when(dataSetService.existsById(DATASET_ID)).thenReturn(true);
    when(dataSourceService.existsById(SOURCE_ID)).thenReturn(true);
    when(dataStructureService.existsById(any())).thenReturn(true);
  }

  /** The record of a tree map package, with one line for each artifact in the install order. */
  private static UseCase treeMap() {
    UseCase useCase = new UseCase();
    useCase.setId(USE_CASE_ID);
    useCase.setPackageId("tree-map");
    useCase.setPackageVersion("1.0.0");
    useCase.addArtifact(line(UseCaseArtifactType.DATA_STRUCTURE, "Cadastre row", ROW_ID));
    useCase.addArtifact(line(UseCaseArtifactType.DATA_STRUCTURE, "Street tree", TREE_ID));
    useCase.addArtifact(line(UseCaseArtifactType.DATA_SOURCE, "Cadastre", SOURCE_ID));
    useCase.addArtifact(line(UseCaseArtifactType.DATA_SET, "Street trees", DATASET_ID));
    useCase.addArtifact(line(UseCaseArtifactType.NAMED_API, "Tree map", API_ID));
    useCase.addArtifact(line(UseCaseArtifactType.MAPPING, "Row to tree", null));
    useCase.addArtifact(line(UseCaseArtifactType.DATA_SINK, "Tree table", SINK_ID));
    useCase.addArtifact(line(UseCaseArtifactType.PIPELINE, "Tree import", PIPELINE_ID));
    useCase.addArtifact(line(UseCaseArtifactType.STYLE, "trees-green", STYLE_ID));
    useCase.addArtifact(line(UseCaseArtifactType.LAYER, "street_trees", LAYER_ID));
    return useCase;
  }

  private static UseCaseArtifact line(UseCaseArtifactType type, String name, UUID shellId) {
    UseCaseArtifact line = new UseCaseArtifact();
    line.setArtifactType(type);
    line.setName(name);
    line.setShellId(shellId);
    return line;
  }
}
