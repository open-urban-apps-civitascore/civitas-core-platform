package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionMetaInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.VersionBump;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.HashMap;
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

  private static final String MODEL_URN =
      "urn:core:platform:civitas:element:common:Sensor:m8i4hc3h56:1.0.0";
  private static final String BLOCKER_URN =
      "urn:core:platform:civitas:datasink:common:Store:4rrb1hifsm";

  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private DataStructureVersionMapper dataStructureVersionMapper;
  @Mock private DataStructureService dataStructureService;
  @Mock private DataSourceRepository dataSourceRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private ArtifactUsageLookup artifactUsageLookup;

  private static final ArtifactUsageLookup.ArtifactUsage IN_USE_BY_RELEASED =
      new ArtifactUsageLookup.ArtifactUsage(
          true,
          List.of(
              new ArtifactUsageLookup.ReleasedReferrer(
                  ArtifactUsageLookup.ReferrerKind.MAPPING,
                  "urn:core:platform:civitas:mapping:common:Map:5tt3mnq2wa")));
  private static final ArtifactUsageLookup.ArtifactUsage IN_USE_BY_DRAFTS =
      new ArtifactUsageLookup.ArtifactUsage(true, List.of());

  @InjectMocks private DataStructureVersionService dataStructureVersionService;

  @BeforeEach
  void stubModelStore() {
    // Model Forge is the version authority: storing a model returns the assigned pin. Lenient so
    // tests that never store a model (early-throw / no-model paths) don't trip strict stubbing.
    lenient()
        .when(modelRegistryGateway.storeModel(any(), any(), any(), any(), any(), any()))
        .thenReturn(
            new ModelRegistryGateway.ModelPin(
                "urn:core:platform:civitas:element:common:test",
                "urn:core:platform:civitas:element:common:test:1.0.0",
                "1.0.0"));
    lenient()
        .when(artifactUsageLookup.of(any(DataStructureVersion.class)))
        .thenReturn(new ArtifactUsageLookup.ArtifactUsage(false, List.of()));
  }

  @Nested
  @DisplayName("Unrelease inUse guard")
  class UnreleaseInUseTests {

    @Test
    @DisplayName("Should block unrelease when a released entity references the version")
    void shouldBlockUnreleaseWhenInUseByReleased() {
      UUID versionId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(artifactUsageLookup.of(version)).thenReturn(IN_USE_BY_RELEASED);

      assertThatThrownBy(() -> dataStructureVersionService.unrelease(versionId))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("a Mapping used by a released Dataset");
    }

    @Test
    @DisplayName("Should allow unrelease when only drafts reference the version")
    void shouldAllowUnreleaseWhenInUseByDraftsOnly() {
      UUID versionId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      DataStructureVersion otherVersion = new DataStructureVersion();
      otherVersion.setId(UUID.randomUUID());
      otherVersion.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      otherVersion.setDataStructure(ds);
      ds.setDataStructureVersions(Set.of(version, otherVersion));

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(artifactUsageLookup.of(version)).thenReturn(IN_USE_BY_DRAFTS);
      when(dataStructureVersionRepository.save(version)).thenReturn(version);

      dataStructureVersionService.unrelease(versionId);

      verify(dataStructureVersionRepository).save(version);
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
    @DisplayName("Should block delete when the registry still holds a reference")
    void shouldBlockDeleteWhenStillReferenced() {
      UUID versionId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.DRAFT);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setDataStructure(ds);
      version.setModelUrn(MODEL_URN);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.existsById(versionId)).thenReturn(true);
      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of(BLOCKER_URN));

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
  @DisplayName("updateReleasedMeta()")
  class UpdateReleasedMetaTests {

    @Test
    @DisplayName("Should change description and keep the model pin")
    void updateReleasedMeta_whenNotInUse_keepsModelPin() {
      // A released version that no released entity uses is the case that could replace its model;
      // without this test a meta path routed through the full update would replace it.
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setVersion("1.0.0");
      version.setModelName("OldModel");
      version.setModelUrn("urn:core:platform:civitas:element:common:test:1.0.0");
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);

      DataStructureVersionMetaInputDTO meta = new DataStructureVersionMetaInputDTO();
      meta.setDescription("New description");

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataStructureVersion result = dataStructureVersionService.updateReleasedMeta(versionId, meta);

      assertThat(result.getDescription()).isEqualTo("New description");
      assertThat(result.getModelName()).isEqualTo("OldModel");
      assertThat(result.getModelUrn())
          .isEqualTo("urn:core:platform:civitas:element:common:test:1.0.0");
      assertThat(result.getVersion()).isEqualTo("1.0.0");
      verifyNoInteractions(modelRegistryGateway);
    }

    @Test
    @DisplayName("Should reject updateReleasedMeta for DRAFT version")
    void shouldRejectUpdateReleasedMetaForDraftVersion() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

      DataStructureVersionMetaInputDTO meta = new DataStructureVersionMetaInputDTO();

      assertThatThrownBy(() -> dataStructureVersionService.updateReleasedMeta(versionId, meta))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DRAFT");
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

      // Releasing changes status only. Asserted rather than assumed, so adding a registry call here
      // fails the test instead of silently changing what a release does.
      verifyNoInteractions(modelRegistryGateway);
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
      input.setDataStructureId(dataStructureId);
      input.setModel(new HashMap<>(Map.of("title", "Observation")));
      input.setStyles(new HashMap<>(Map.of("color", "blue")));

      DataStructureVersion newEntity = new DataStructureVersion();

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
              any(),
              any());
      assertThat(result.getVersion()).isEqualTo("1.0.0");
      assertThat(result.getModelUrn())
          .isEqualTo("urn:core:platform:civitas:element:common:test:1.0.0");
      assertThat(dataStructure.getModelLogicalUrn())
          .as("The parent's stable logical URN is minted on the first store")
          .isEqualTo("urn:core:platform:civitas:element:common:test");
    }

    @Test
    @DisplayName("A version's first model starts a new major")
    void createStartsANewMajor() {
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dataStructureId);
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      dataStructure.setModelLogicalUrn("urn:core:platform:civitas:element:common:test");

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureId(dataStructureId);
      input.setModel(new HashMap<>(Map.of("title", "Observation")));

      DataStructureVersion newEntity = new DataStructureVersion();
      newEntity.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);

      when(dataStructureVersionMapper.toEntity(any())).thenReturn(newEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);
      when(dataStructureVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      dataStructureVersionService.create(input);

      verify(modelRegistryGateway)
          .storeModel(
              eq(Optional.of("urn:core:platform:civitas:element:common:test")),
              any(),
              any(),
              any(),
              eq(VersionBump.MAJOR),
              any());
    }

    @Test
    @DisplayName("A model that arrives on a later save still starts a new major")
    void lateModelStillStartsANewMajor() {
      UUID versionId = UUID.randomUUID();
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dataStructureId);
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      dataStructure.setModelLogicalUrn("urn:core:platform:civitas:element:common:test");

      // Created without a diagram, so it holds no model yet — the save below is its first.
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setDataStructure(dataStructure);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureId(dataStructureId);
      input.setModel(new HashMap<>(Map.of("title", "Arrived late")));

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);

      dataStructureVersionService.update(versionId, input);

      verify(modelRegistryGateway)
          .storeModel(any(), any(), any(), any(), eq(VersionBump.MAJOR), isNull());
    }

    @Test
    @DisplayName("Changing a model a version already has advances its minor")
    void editAdvancesTheMinor() {
      UUID versionId = UUID.randomUUID();
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dataStructureId);
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      dataStructure.setModelLogicalUrn("urn:core:platform:civitas:element:common:test");

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setVersion("2.4.0");
      version.setModelUrn("urn:core:platform:civitas:element:common:test:2.4.0");
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setDataStructure(dataStructure);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureId(dataStructureId);
      input.setModel(new HashMap<>(Map.of("title", "Replaced")));

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);

      dataStructureVersionService.update(versionId, input);

      verify(modelRegistryGateway)
          .storeModel(any(), any(), any(), any(), eq(VersionBump.MINOR), eq("2.4.0"));
    }

    @Test
    @DisplayName("Create without a model does not touch Model Forge and leaves the version unset")
    void createWithoutModelSkipsRegistry() {
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dataStructureId);
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureId(dataStructureId);

      DataStructureVersion newEntity = new DataStructureVersion();

      when(dataStructureVersionMapper.toEntity(any())).thenReturn(newEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);
      when(dataStructureVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataStructureVersion result = dataStructureVersionService.create(input);

      verify(modelRegistryGateway, org.mockito.Mockito.never())
          .storeModel(any(), any(), any(), any(), any(), any());
      assertThat(result.getVersion()).isNull();
      assertThat(result.getModelUrn()).isNull();
    }
  }
}
