package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.VersionBump;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataStructureVersionService Unit Tests")
class DataStructureVersionServiceTest {

  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private DataStructureVersionMapper dataStructureVersionMapper;
  @Mock private DataStructureService dataStructureService;
  @Mock private DataSourceRepository dataSourceRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;

  @InjectMocks private DataStructureVersionService dataStructureVersionService;

  @BeforeEach
  void stubModelStore() {
    // Model Forge is the version authority: storing a model returns the assigned pin. Lenient so
    // tests that never store a model (early-throw / no-model paths) don't trip strict stubbing.
    lenient()
        .when(modelRegistryGateway.storeModel(any(), any(), any(), any(), any()))
        .thenReturn(
            new ModelRegistryGateway.ModelPin(
                "urn:core:platform:civitas:element:common:test",
                "urn:core:platform:civitas:element:common:test:1.0.0",
                "1.0.0"));
  }

  @Nested
  @DisplayName("Unrelease inUse guard")
  class UnreleaseInUseTests {

    @Test
    @DisplayName("Should block unrelease when version is in use by a DataSource")
    void shouldBlockUnreleaseWhenInUse() {
      UUID versionId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(true);

      assertThatThrownBy(() -> dataStructureVersionService.unrelease(versionId))
          .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    @DisplayName("Should block unrelease when version is in use by a DataSink")
    void shouldBlockUnreleaseWhenInUseByDataSink() {
      UUID versionId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(modelRegistryGateway.isReferencedBySink(any())).thenReturn(true);

      assertThatThrownBy(() -> dataStructureVersionService.unrelease(versionId))
          .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    @DisplayName("Should allow unrelease when version is not in use")
    void shouldAllowUnreleaseWhenNotInUse() {
      UUID versionId = UUID.randomUUID();
      UUID otherVersionId = UUID.randomUUID();

      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      DataStructureVersion otherVersion = new DataStructureVersion();
      otherVersion.setId(otherVersionId);
      otherVersion.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      otherVersion.setDataStructure(ds);

      ds.setDataStructureVersions(Set.of(version, otherVersion));

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(false);
      when(dataStructureVersionRepository.save(version)).thenReturn(version);

      dataStructureVersionService.unrelease(versionId);

      verify(dataStructureVersionRepository).save(version);
    }
  }

  @Nested
  @DisplayName("Delete inUse guard")
  class DeleteInUseTests {

    @Test
    @DisplayName("Should block delete when version is in use by a DataSource")
    void shouldBlockDeleteWhenInUse() {
      UUID versionId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.DRAFT);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setDataStructure(ds);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.existsById(versionId)).thenReturn(true);
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(true);

      assertThatThrownBy(() -> dataStructureVersionService.deleteById(versionId))
          .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    @DisplayName("Should block delete when version is in use by a DataSink")
    void shouldBlockDeleteWhenInUseByDataSink() {
      UUID versionId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.DRAFT);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setDataStructure(ds);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.existsById(versionId)).thenReturn(true);
      when(modelRegistryGateway.isReferencedBySink(any())).thenReturn(true);

      assertThatThrownBy(() -> dataStructureVersionService.deleteById(versionId))
          .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    @DisplayName("Should allow delete when version is not in use")
    void shouldAllowDeleteWhenNotInUse() {
      UUID versionId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.DRAFT);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setDataStructure(ds);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.existsById(versionId)).thenReturn(true);
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(false);

      dataStructureVersionService.deleteById(versionId);

      verify(dataStructureVersionRepository).deleteById(versionId);
    }
  }

  @Nested
  @DisplayName("UpdateReleasedMeta inUse guard")
  class UpdateReleasedMetaInUseTests {

    @Test
    @DisplayName("Should allow full update via updateReleasedMeta when version is not in use")
    void shouldAllowFullUpdateWhenNotInUse() {
      UUID versionId = UUID.randomUUID();
      UUID dataStructureId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setId(dataStructureId);
      ds.setModelLogicalUrn("urn:core:platform:civitas:element:common:test");
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setVersion("1.0.0");
      version.setModelName("OldModel");
      version.setModelUrn("urn:core:platform:civitas:element:common:test:1.0.0");
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setModel(new HashMap<>(Map.of("title", "New")));
      input.setStyles(new HashMap<>(Map.of("color", "red")));

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(false);
      when(dataStructureVersionRepository.save(any())).thenReturn(version);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(ds);

      assertThatNoException()
          .isThrownBy(() -> dataStructureVersionService.updateReleasedMeta(versionId, input));

      // The replaced model is stored as a new registry version under the parent's logical URN.
      verify(modelRegistryGateway)
          .storeModel(
              eq(Optional.of("urn:core:platform:civitas:element:common:test")),
              any(),
              eq(Map.of("title", "New")),
              eq(Map.of("color", "red")),
              any());
    }

    @Test
    @DisplayName("Should block model and structural changes via updateReleasedMeta when in use")
    void shouldBlockStructuralChangesWhenInUse() {
      UUID versionId = UUID.randomUUID();
      UUID dataStructureId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setId(dataStructureId);
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setVersion("1.0.0");
      version.setModelName("OldModel");
      version.setModelUrn("urn:core:platform:civitas:element:common:original:1.0.0");
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setModel(new HashMap<>(Map.of("title", "SHOULD_NOT_CHANGE")));
      input.setStyles(new HashMap<>(Map.of("color", "red")));
      input.setModelName("UpdatedModelName");

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(true);
      when(dataStructureVersionRepository.save(any())).thenReturn(version);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(ds);

      dataStructureVersionService.updateReleasedMeta(versionId, input);

      // In-use: the structural payload is neutralized — no registry write, the pin is preserved.
      verify(modelRegistryGateway, org.mockito.Mockito.never())
          .storeModel(any(), any(), any(), any(), any());
      assertThat(input.getModel()).as("In-use model change must be dropped").isNull();
      assertThat(input.getStyles()).as("In-use styles change must be dropped").isNull();
      assertThat(version.getModelUrn())
          .as("The stored content pin stays untouched")
          .isEqualTo("urn:core:platform:civitas:element:common:original:1.0.0");
      assertThat(input.getModelName())
          .as("ModelName is editable while in use")
          .isEqualTo("UpdatedModelName");
    }

    @Test
    @DisplayName("Should reject updateReleasedMeta for DRAFT version")
    void shouldRejectUpdateReleasedMetaForDraftVersion() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();

      assertThatThrownBy(() -> dataStructureVersionService.updateReleasedMeta(versionId, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("Should reject clearing the model of a released version that is not in use")
    void shouldRejectClearingModelWhenReleasedAndNotInUse() {
      UUID versionId = UUID.randomUUID();
      UUID dataStructureId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setId(dataStructureId);
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setVersion("1.0.0");
      version.setModelUrn("urn:core:platform:civitas:element:common:existing:1.0.0");
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setModel(null);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(false);

      assertThatThrownBy(() -> dataStructureVersionService.updateReleasedMeta(versionId, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("model");
    }
  }

  @Nested
  @DisplayName("Release model guard")
  class ReleaseModelGuardTests {

    @Test
    @DisplayName("Should block release when version has no stored model (no registry pin)")
    void shouldBlockReleaseWhenModelIsNull() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

      assertThatThrownBy(() -> dataStructureVersionService.release(versionId))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("model");
    }

    @Test
    @DisplayName("Should allow release when version carries a stored model (registry pin)")
    void shouldAllowReleaseWhenModelPresent() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setModelUrn("urn:core:platform:civitas:element:common:observation:1.0.0");

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.save(version)).thenReturn(version);

      DataStructureVersion result = dataStructureVersionService.release(versionId);

      assertThat(result.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
      verify(dataStructureVersionRepository).save(version);
    }

    @Test
    @DisplayName("Should block release when version is already released")
    void shouldBlockReleaseWhenAlreadyReleased() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

      assertThatThrownBy(() -> dataStructureVersionService.release(versionId))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("already released");
    }
  }

  @Nested
  @DisplayName("Model store on create/update (Model Forge is the version authority)")
  class ModelStoreTests {

    @Test
    @DisplayName(
        "Create stores the model in Model Forge and mirrors the assigned pin onto the shell")
    void createStoresModelAndMirrorsPin() {
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dataStructureId);
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setModel(new HashMap<>(Map.of("title", "Observation")));
      input.setStyles(new HashMap<>(Map.of("color", "blue")));

      DataStructureVersion newEntity = new DataStructureVersion();
      newEntity.setDataStructureVersionSource(DataStructureVersionSource.OWN);

      when(dataStructureVersionMapper.toEntity(any())).thenReturn(newEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);
      when(dataStructureVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataStructureVersion result = dataStructureVersionService.create(input);

      // Model and styles went to Model Forge (the gateway merges styles as x-ui-styles), and the
      // assigned version + URN were mirrored onto the shell.
      verify(modelRegistryGateway)
          .storeModel(
              eq(Optional.empty()),
              any(),
              eq(Map.of("title", "Observation")),
              eq(Map.of("color", "blue")),
              any());
      assertThat(result.getVersion()).isEqualTo("1.0.0");
      assertThat(result.getModelUrn())
          .isEqualTo("urn:core:platform:civitas:element:common:test:1.0.0");
      assertThat(dataStructure.getModelLogicalUrn())
          .as("The parent's stable logical URN is minted on the first store")
          .isEqualTo("urn:core:platform:civitas:element:common:test");
    }

    @Test
    @DisplayName("The input's version bump reaches the gateway; absent bump defaults to MINOR")
    void versionBumpFromInputReachesGateway() {
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dataStructureId);
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      dataStructure.setModelLogicalUrn("urn:core:platform:civitas:element:common:test");

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setModel(new HashMap<>(Map.of("title", "Observation")));

      DataStructureVersion newEntity = new DataStructureVersion();
      newEntity.setDataStructureVersionSource(DataStructureVersionSource.OWN);

      when(dataStructureVersionMapper.toEntity(any())).thenReturn(newEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);
      when(dataStructureVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // The DTO defaults to MINOR when the client sends no bump.
      assertThat(input.getVersionBump()).isEqualTo(VersionBump.MINOR);

      input.setVersionBump(VersionBump.MAJOR);
      dataStructureVersionService.create(input);

      verify(modelRegistryGateway)
          .storeModel(
              eq(Optional.of("urn:core:platform:civitas:element:common:test")),
              any(),
              any(),
              any(),
              eq(VersionBump.MAJOR));
    }

    @Test
    @DisplayName("Create without a model does not touch Model Forge and gets a provisional version")
    void createWithoutModelSkipsRegistry() {
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dataStructureId);
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);

      DataStructureVersion newEntity = new DataStructureVersion();
      newEntity.setDataStructureVersionSource(DataStructureVersionSource.OWN);

      when(dataStructureVersionMapper.toEntity(any())).thenReturn(newEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);
      when(dataStructureVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataStructureVersion result = dataStructureVersionService.create(input);

      verify(modelRegistryGateway, org.mockito.Mockito.never())
          .storeModel(any(), any(), any(), any(), any());
      // Never null: the portal frontend sorts on the version string and dies on null. The
      // provisional is overwritten by the registry pin on the first model store.
      assertThat(result.getVersion()).isEqualTo("1.0.0-draft");
      assertThat(result.getModelUrn()).isNull();
    }
  }

  @Nested
  @DisplayName("Provisional version for model-less drafts")
  class ProvisionalVersionTests {

    private DataStructure parentWithVersions(String... siblingVersions) {
      DataStructure parent = new DataStructure();
      parent.setId(UUID.randomUUID());
      HashSet<DataStructureVersion> siblings = new HashSet<>();
      for (String v : siblingVersions) {
        DataStructureVersion sibling = new DataStructureVersion();
        sibling.setId(UUID.randomUUID());
        sibling.setVersion(v);
        siblings.add(sibling);
      }
      parent.setDataStructureVersions(siblings);
      when(dataStructureService.findByIdOrThrow(parent.getId())).thenReturn(parent);
      lenient().when(modelRegistryGateway.validateSchema(null)).thenReturn(List.of());
      return parent;
    }

    private DataStructureVersion convert(DataStructure parent) {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureId(parent.getId());
      return dataStructureVersionService.postConvertToEntity(new DataStructureVersion(), input);
    }

    @Test
    @DisplayName("first version of a structure gets 1.0.0-draft")
    void firstDraftGetsBaseProvisional() {
      DataStructure parent = parentWithVersions();

      DataStructureVersion entity = convert(parent);

      assertThat(entity.getVersion()).isEqualTo("1.0.0-draft");
    }

    @Test
    @DisplayName("provisional bumps the highest sibling patch")
    void provisionalBumpsHighestSibling() {
      DataStructure parent = parentWithVersions("1.0.0", "1.0.2");

      DataStructureVersion entity = convert(parent);

      assertThat(entity.getVersion()).isEqualTo("1.0.3-draft");
    }

    @Test
    @DisplayName("provisional siblings count too, so parallel drafts do not collide")
    void provisionalSiblingsAreCounted() {
      DataStructure parent = parentWithVersions("1.0.0", "1.0.1-draft");

      DataStructureVersion entity = convert(parent);

      assertThat(entity.getVersion()).isEqualTo("1.0.2-draft");
    }

    @Test
    @DisplayName("unparseable sibling versions are ignored")
    void unparseableSiblingsAreIgnored() {
      DataStructure parent = parentWithVersions("kaputt", "2.x");

      DataStructureVersion entity = convert(parent);

      assertThat(entity.getVersion()).isEqualTo("1.0.0-draft");
    }

    @Test
    @DisplayName("a stored model keeps the registry-minted version — no provisional")
    void registryPinWinsWhenModelIsStored() {
      DataStructure parent = parentWithVersions();
      when(modelRegistryGateway.validateSchema(any(Map.class))).thenReturn(List.of());
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureId(parent.getId());
      input.setModel(Map.of("type", "object"));

      DataStructureVersion entity =
          dataStructureVersionService.postConvertToEntity(new DataStructureVersion(), input);

      assertThat(entity.getVersion()).isEqualTo("1.0.0");
    }
  }
}
