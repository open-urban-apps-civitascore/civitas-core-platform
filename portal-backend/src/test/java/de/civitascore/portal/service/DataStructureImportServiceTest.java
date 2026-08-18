package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.input.DataStructureImportInputDTO;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
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
 * Unit tests for {@link DataStructureImportService}: a thin orchestration over the existing
 * structure and version services. The services are mocked; these tests pin how the import input is
 * split into the two create calls (field threading, forced defaults, ordering) and the {@code $id}
 * guard that keeps models without a datastructure identity out of the registry — transactional
 * rollback is the container's job and is not unit-testable here.
 */
@ExtendWith(MockitoExtension.class)
class DataStructureImportServiceTest {

  private static final String DATASTRUCTURE_URN =
      "urn:core:standard:openurbanapps:datastructure:environment:airqualitystation:default";
  private static final String ELEMENT_URN =
      "urn:core:standard:openurbanapps:element:environment:messwert:default";

  @Mock private DataStructureService dataStructureService;
  @Mock private DataStructureVersionService dataStructureVersionService;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private DataStructureRepository dataStructureRepository;
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private InstallationRecorder installationRecorder;
  @InjectMocks private DataStructureImportService importService;

  /** Stubs the guard chain for inputs that carry the valid datastructure URN. */
  private void stubValidUrn() {
    when(modelRegistryGateway.isDataStructureUrn(DATASTRUCTURE_URN)).thenReturn(true);
    when(modelRegistryGateway.logicalUrn(DATASTRUCTURE_URN)).thenReturn(DATASTRUCTURE_URN);
  }

  /**
   * A version as the create path returns it: pinned to a shell row and to a registry version. Pure
   * factory on purpose — a helper that also stubs cannot be passed to {@code thenReturn(...)}:
   * Mockito rejects that as nested stubbing.
   */
  private static DataStructureVersion createdVersion(UUID structureId) {
    DataStructure structure = new DataStructure();
    structure.setId(structureId);
    DataStructureVersion version = new DataStructureVersion();
    version.setModelUrn(DATASTRUCTURE_URN + ":1.0.0");
    version.setDataStructure(structure);
    return version;
  }

  @Captor private ArgumentCaptor<DataStructureInputDTO> structureInputCaptor;
  @Captor private ArgumentCaptor<DataStructureVersionInputDTO> versionInputCaptor;
  @Captor private ArgumentCaptor<List<InstalledArtifact>> artifactLinesCaptor;

  private static DataStructureImportInputDTO importInput(Map<String, Object> model) {
    DataStructureImportInputDTO input = new DataStructureImportInputDTO();
    input.setName("Air Quality Station");
    input.setDescription("Structure description");
    input.setVersionDescription("Version description");
    input.setModelName("AirQualityStation");
    input.setModel(model);
    input.setStyles(Map.of("nodes", Map.of()));
    return input;
  }

  private static DataStructureImportInputDTO importInput() {
    return importInput(Map.of("$id", DATASTRUCTURE_URN, "$defs", Map.of()));
  }

  @Test
  void importDataStructure_threadsFieldsIntoBothCreates() {
    stubValidUrn();
    UUID structureId = UUID.randomUUID();
    DataStructure structure = new DataStructure();
    structure.setId(structureId);
    when(dataStructureService.create(any(DataStructureInputDTO.class))).thenReturn(structure);
    DataStructureVersion version = createdVersion(structureId);
    when(dataStructureVersionService.create(any(DataStructureVersionInputDTO.class)))
        .thenReturn(version);

    DataStructureVersion result = importService.importDataStructure(importInput());

    assertThat(result).isSameAs(version);
    verify(dataStructureService).create(structureInputCaptor.capture());
    DataStructureInputDTO structureInput = structureInputCaptor.getValue();
    assertThat(structureInput.getName()).isEqualTo("Air Quality Station");
    assertThat(structureInput.getDescription()).isEqualTo("Structure description");
    assertThat(structureInput.getCreatedFromDataSource()).isFalse();

    verify(dataStructureVersionService).create(versionInputCaptor.capture());
    DataStructureVersionInputDTO versionInput = versionInputCaptor.getValue();
    assertThat(versionInput.getDataStructureId()).isEqualTo(structureId);
    assertThat(versionInput.getDataStructureVersionSource())
        .isEqualTo(DataStructureVersionSource.OWN);
    assertThat(versionInput.getDescription()).isEqualTo("Version description");
    assertThat(versionInput.getModelName()).isEqualTo("AirQualityStation");
    assertThat(versionInput.getModel()).containsKey("$defs");
    assertThat(versionInput.getStyles()).containsKey("nodes");
  }

  /**
   * The single-artifact path records provenance too, so "installed" means the same thing whichever
   * endpoint did it. It produces no dataset, which is why the header carries none.
   */
  @Test
  void importDataStructure_recordsProvenanceWithoutADataset() {
    stubValidUrn();
    UUID structureId = UUID.randomUUID();
    DataStructure structure = new DataStructure();
    structure.setId(structureId);
    when(dataStructureService.create(any(DataStructureInputDTO.class))).thenReturn(structure);
    when(dataStructureVersionService.create(any(DataStructureVersionInputDTO.class)))
        .thenReturn(createdVersion(structureId));
    // The recorded line carries the logical URN, not the versioned one Model Forge returns.
    when(modelRegistryGateway.logicalUrn(DATASTRUCTURE_URN + ":1.0.0"))
        .thenReturn(DATASTRUCTURE_URN);
    DataStructureImportInputDTO input = importInput();
    input.setBundleId("urn:openurbanapps:datastructure:airqualitystation");
    input.setBundleVersion("1.0.0");

    importService.importDataStructure(input);

    verify(installationRecorder)
        .record(
            eq("urn:openurbanapps:datastructure:airqualitystation"),
            eq("1.0.0"),
            isNull(),
            isNull(),
            artifactLinesCaptor.capture());
    assertThat(artifactLinesCaptor.getValue())
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.getArtifactType()).isEqualTo(InstalledArtifactType.DATA_STRUCTURE);
              assertThat(line.getShellId()).isEqualTo(structureId);
              assertThat(line.getUrn()).isEqualTo(DATASTRUCTURE_URN);
              assertThat(line.getVersionedUrn()).isEqualTo(DATASTRUCTURE_URN + ":1.0.0");
              assertThat(line.getAction()).isEqualTo(InstalledArtifactAction.CREATED);
            });
  }

  @Test
  void importDataStructure_whenStructureCreateFails_neverCreatesVersion() {
    stubValidUrn();
    when(dataStructureService.create(any(DataStructureInputDTO.class)))
        .thenThrow(
            new InvalidInputException("DataStructure", "name", "Name cannot be null or blank"));

    assertThatThrownBy(() -> importService.importDataStructure(importInput()))
        .isInstanceOf(InvalidInputException.class);

    verify(dataStructureVersionService, never()).create(any());
  }

  @Test
  void importDataStructure_withoutModelId_rejectsBeforeCreatingAnything() {
    assertThatThrownBy(
            () -> importService.importDataStructure(importInput(Map.of("$defs", Map.of()))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("$id");

    verify(dataStructureService, never()).create(any());
    verify(dataStructureVersionService, never()).create(any());
  }

  @Test
  void importDataStructure_withElementId_rejectsBeforeCreatingAnything() {
    // Default mock behaviour: isDataStructureUrn(ELEMENT_URN) returns false.
    assertThatThrownBy(
            () -> importService.importDataStructure(importInput(Map.of("$id", ELEMENT_URN))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("datastructure");

    verify(dataStructureService, never()).create(any());
    verify(dataStructureVersionService, never()).create(any());
  }

  @Test
  void importDataStructure_withNonStringModelId_rejectsBeforeCreatingAnything() {
    assertThatThrownBy(() -> importService.importDataStructure(importInput(Map.of("$id", 42))))
        .isInstanceOf(InvalidInputException.class);

    verify(dataStructureService, never()).create(any());
    verify(dataStructureVersionService, never()).create(any());
  }

  @Test
  void importDataStructure_whenModelAlreadyInstalled_rejectsWith409() {
    stubValidUrn();
    when(dataStructureRepository.existsByModelLogicalUrn(DATASTRUCTURE_URN)).thenReturn(true);

    assertThatThrownBy(() -> importService.importDataStructure(importInput()))
        .isInstanceOf(UniqueConstraintViolationException.class)
        .hasMessageContaining("already installed");

    verify(dataStructureService, never()).create(any());
    verify(dataStructureVersionService, never()).create(any());
  }

  @Test
  void importOrReuse_whenIdentityInstalledWithIdenticalContent_reusesWithoutCreating() {
    stubValidUrn();
    DataStructureVersion installed = new DataStructureVersion();
    installed.setModelUrn(DATASTRUCTURE_URN + ":1.0.0");
    when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(DATASTRUCTURE_URN + ":"))
        .thenReturn(Optional.of(installed));
    when(modelRegistryGateway.isUnchanged(
            org.mockito.ArgumentMatchers.eq(DATASTRUCTURE_URN + ":1.0.0"), any(), any()))
        .thenReturn(true);

    DataStructureImportService.ImportResolution resolution =
        importService.importOrReuse(importInput());

    assertThat(resolution.reused()).isTrue();
    assertThat(resolution.version()).isSameAs(installed);
    verify(dataStructureService, never()).create(any());
    verify(dataStructureVersionService, never()).create(any());
  }

  @Test
  void importOrReuse_whenIdentityInstalledWithDifferentContent_rejectsWith409() {
    stubValidUrn();
    DataStructureVersion installed = new DataStructureVersion();
    installed.setModelUrn(DATASTRUCTURE_URN + ":1.0.0");
    when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(DATASTRUCTURE_URN + ":"))
        .thenReturn(Optional.of(installed));
    when(modelRegistryGateway.isUnchanged(
            org.mockito.ArgumentMatchers.eq(DATASTRUCTURE_URN + ":1.0.0"), any(), any()))
        .thenReturn(false);

    assertThatThrownBy(() -> importService.importOrReuse(importInput()))
        .isInstanceOf(UniqueConstraintViolationException.class)
        .hasMessageContaining("different content");

    verify(dataStructureService, never()).create(any());
  }

  @Test
  void importOrReuse_whenIdentityUnknown_createsLikeTheSingleImport() {
    stubValidUrn();
    when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(DATASTRUCTURE_URN + ":"))
        .thenReturn(Optional.empty());
    DataStructure structure = new DataStructure();
    structure.setId(UUID.randomUUID());
    when(dataStructureService.create(any(DataStructureInputDTO.class))).thenReturn(structure);
    DataStructureVersion created = new DataStructureVersion();
    when(dataStructureVersionService.create(any(DataStructureVersionInputDTO.class)))
        .thenReturn(created);

    DataStructureImportService.ImportResolution resolution =
        importService.importOrReuse(importInput());

    assertThat(resolution.reused()).isFalse();
    assertThat(resolution.version()).isSameAs(created);
  }

  private static DataStructureVersion versionWithStatuses(
      DataStructureVersionStatus versionStatus, DataStructureStatus structureStatus) {
    DataStructure structure = new DataStructure();
    structure.setId(UUID.randomUUID());
    structure.setDataStructureStatus(structureStatus);
    DataStructureVersion version = new DataStructureVersion();
    version.setId(UUID.randomUUID());
    version.setDataStructureVersionStatus(versionStatus);
    version.setDataStructure(structure);
    return version;
  }

  @Test
  void ensureAvailable_releasesDraftVersionBeforeDraftStructure() {
    DataStructureVersion version =
        versionWithStatuses(DataStructureVersionStatus.DRAFT, DataStructureStatus.DRAFT);

    importService.ensureAvailable(version);

    // Version first: the structure's release validation requires a released version.
    var order = inOrder(dataStructureVersionService, dataStructureService);
    order.verify(dataStructureVersionService).release(version.getId());
    order.verify(dataStructureService).release(version.getDataStructure().getId());
  }

  @Test
  void ensureAvailable_isNoOpWhenBothAreAvailable() {
    DataStructureVersion version =
        versionWithStatuses(DataStructureVersionStatus.AVAILABLE, DataStructureStatus.AVAILABLE);

    importService.ensureAvailable(version);

    verify(dataStructureVersionService, never()).release(any(UUID.class));
    verify(dataStructureService, never()).release(any(UUID.class));
  }

  @Test
  void ensureAvailable_releasesOnlyTheDraftHalf() {
    DataStructureVersion version =
        versionWithStatuses(DataStructureVersionStatus.AVAILABLE, DataStructureStatus.DRAFT);

    importService.ensureAvailable(version);

    verify(dataStructureVersionService, never()).release(any(UUID.class));
    verify(dataStructureService).release(version.getDataStructure().getId());
  }
}
