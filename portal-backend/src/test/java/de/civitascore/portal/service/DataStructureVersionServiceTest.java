package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionMetaInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.VersionBump;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.security.ModelReferenceAuthorizer;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ModelReferenceNotAvailableException;
import de.civitascore.portal.util.ModelRootMissingException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataStructureVersionService Unit Tests")
class DataStructureVersionServiceTest {

  private static final String MODEL_URN =
      "urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36:1.0.0";
  private static final String BLOCKER_URN =
      "urn:core:platform:civitas:datasink:common:762a05c3-4783-8446-b3cb-8240eee1227a";

  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private DataStructureVersionMapper dataStructureVersionMapper;
  @Mock private DataStructureService dataStructureService;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private ArtifactUsageLookup artifactUsageLookup;
  @Mock private ModelReferenceAuthorizer modelReferenceAuthorizer;

  private static final ArtifactUsageLookup.ArtifactUsage IN_USE_BY_RELEASED =
      new ArtifactUsageLookup.ArtifactUsage(
          true,
          List.of(
              new ArtifactUsageLookup.ReleasedReferrer(
                  ArtifactUsageLookup.ReferrerKind.MAPPING,
                  "urn:core:platform:civitas:mapping:common:b2ecab01-2f5a-8963-ac6a-2f86b3c8205a")));
  private static final ArtifactUsageLookup.ArtifactUsage IN_USE_BY_DRAFTS =
      new ArtifactUsageLookup.ArtifactUsage(true, List.of());

  @InjectMocks private DataStructureVersionService dataStructureVersionService;

  @BeforeEach
  void stubModelStore() {
    // Model Forge is the version authority: storing a model returns the assigned pin. Lenient so
    // tests that never store a model (early-throw / no-model paths) don't trip strict stubbing.
    lenient()
        .when(modelRegistryGateway.storeModel(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            new ModelRegistryGateway.ModelPin(
                "urn:core:platform:civitas:element:common:00000000-0000-4000-8000-000000000016",
                "urn:core:platform:civitas:element:common:7c96ac2d-1fad-817a-a9b0-c7374bba1952:1.0.0",
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
    @DisplayName("Should unrelease the only released version when it is not in use")
    void shouldUnreleaseOnlyReleasedVersionWhenNotInUse() {
      UUID versionId = UUID.randomUUID();

      DataStructure ds = new DataStructure();

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      ds.setDataStructureVersions(Set.of(version));

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.save(version)).thenReturn(version);

      dataStructureVersionService.unrelease(versionId);

      verify(dataStructureVersionRepository).save(version);
    }
  }

  @Nested
  @DisplayName("Delete")
  class DeleteTests {

    private static final String LOGICAL_URN =
        "urn:core:platform:civitas:element:common:36b5df47-c3f5-86fd-9428-4597d8cd4d36";

    private final UUID versionId = UUID.randomUUID();
    private final DataStructure parent = new DataStructure();
    private final DataStructureVersion version = new DataStructureVersion();

    @BeforeEach
    void storedVersion() {
      parent.setModelLogicalUrn(LOGICAL_URN);
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setDataStructure(parent);
      version.setModelUrn(MODEL_URN);
      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.existsById(versionId)).thenReturn(true);
    }

    @Test
    @DisplayName("Should block delete and leave the model when the version is in use")
    void deleteById_whenInUse_shouldThrowAndDeleteNothing() {
      when(artifactUsageLookup.of(version)).thenReturn(IN_USE_BY_DRAFTS);

      assertThatThrownBy(() -> dataStructureVersionService.deleteById(versionId))
          .isInstanceOf(ResourceInUseException.class);
      verify(dataStructureVersionRepository, never()).deleteById(any());
      verify(modelRegistryGateway, never()).deleteModelVersion(any());
    }

    @Test
    @DisplayName("Should delete the row and the model's version line")
    void deleteById_whenNotInUse_shouldDeleteTheModelVersion() {
      when(modelRegistryGateway.holds(LOGICAL_URN)).thenReturn(true);

      dataStructureVersionService.deleteById(versionId);

      verify(dataStructureVersionRepository).deleteById(versionId);
      verify(modelRegistryGateway).deleteModelVersion(MODEL_URN);
      assertThat(parent.getModelLogicalUrn()).isEqualTo(LOGICAL_URN);
    }

    @Test
    @DisplayName("Should leave the registry alone for a version without a model")
    void deleteById_withoutModel_shouldSkipTheRegistry() {
      version.setModelUrn(null);

      dataStructureVersionService.deleteById(versionId);

      verify(dataStructureVersionRepository).deleteById(versionId);
      verify(modelRegistryGateway, never()).deleteModelVersion(any());
    }

    @Test
    @DisplayName("Should block delete when another version has its model in the same major")
    void deleteById_whenAnotherVersionSharesTheMajor_shouldThrowAndDeleteNothing() {
      siblingPinnedTo(LOGICAL_URN + ":1.1.0");

      assertThatThrownBy(() -> dataStructureVersionService.deleteById(versionId))
          .isInstanceOf(ResourceInUseException.class);
      verify(dataStructureVersionRepository, never()).deleteById(any());
      verify(modelRegistryGateway, never()).deleteModelVersion(any());
    }

    @Test
    @DisplayName("Should delete when the other versions have their models in other majors")
    void deleteById_whenOtherVersionsUseOtherMajors_shouldDeleteTheModelVersion() {
      siblingPinnedTo(LOGICAL_URN + ":2.0.0");
      when(modelRegistryGateway.holds(LOGICAL_URN)).thenReturn(true);

      dataStructureVersionService.deleteById(versionId);

      verify(modelRegistryGateway).deleteModelVersion(MODEL_URN);
    }

    private void siblingPinnedTo(String pin) {
      parent.setId(UUID.randomUUID());
      DataStructureVersion sibling = new DataStructureVersion();
      sibling.setId(UUID.randomUUID());
      sibling.setDataStructure(parent);
      sibling.setModelUrn(pin);
      when(modelRegistryGateway.logicalUrn(any())).thenReturn(LOGICAL_URN);
      when(dataStructureVersionRepository.findAllByDataStructure_IdIn(List.of(parent.getId())))
          .thenReturn(List.of(version, sibling));
    }

    @Test
    @DisplayName("Should forget the logical URN once the registry holds no version of the model")
    void deleteById_ofTheLastModelVersion_shouldClearTheLogicalUrn() {
      when(modelRegistryGateway.holds(LOGICAL_URN)).thenReturn(false);

      dataStructureVersionService.deleteById(versionId);

      assertThat(parent.getModelLogicalUrn()).isNull();
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
      version.setModelUrn(
          "urn:core:platform:civitas:element:common:7c96ac2d-1fad-817a-a9b0-c7374bba1952:1.0.0");
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);

      DataStructureVersionMetaInputDTO meta = new DataStructureVersionMetaInputDTO();
      meta.setDescription("New description");

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataStructureVersion result = dataStructureVersionService.updateReleasedMeta(versionId, meta);

      assertThat(result.getDescription()).isEqualTo("New description");
      assertThat(result.getModelName()).isEqualTo("OldModel");
      assertThat(result.getModelUrn())
          .isEqualTo(
              "urn:core:platform:civitas:element:common:7c96ac2d-1fad-817a-a9b0-c7374bba1952:1.0.0");
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
    @DisplayName("Should allow release when the stored model has a single root Element")
    void shouldAllowReleaseWhenModelHasSingleRoot() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setModelUrn(
          "urn:core:platform:civitas:element:common:5cf6e9e7-fa0d-877a-bda7-df0cc339e439:1.0.0");
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(UUID.randomUUID());
      version.setDataStructure(dataStructure);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.save(version)).thenReturn(version);

      DataStructureVersion result = dataStructureVersionService.release(versionId);

      assertThat(result.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
      verify(dataStructureVersionRepository).save(version);

      // Releasing only reads the root from the registry. Asserted rather than assumed, so adding a
      // registry write here fails the test instead of silently changing what a release does.
      verify(modelRegistryGateway).requireSingleRoot(version.getModelUrn());
      verifyNoMoreInteractions(modelRegistryGateway);
      verify(modelReferenceAuthorizer)
          .authorizeStored(dataStructure.getId(), version.getModelUrn());
    }

    @Test
    @DisplayName("Should block release when the model references a model the caller may not use")
    void shouldBlockReleaseWhenAReferenceIsNotAvailable() {
      UUID versionId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(UUID.randomUUID());
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setModelUrn(
          "urn:core:platform:civitas:datastructure:common:6f1c2a3e-8d4b-4c5a-9e7f-1a2b3c4d5e6f:1.0.0");
      version.setDataStructure(dataStructure);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      doThrow(new ModelReferenceNotAvailableException())
          .when(modelReferenceAuthorizer)
          .authorizeStored(dataStructure.getId(), version.getModelUrn());

      assertThatThrownBy(() -> dataStructureVersionService.release(versionId))
          .isInstanceOf(ModelReferenceNotAvailableException.class);
      assertThat(version.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.DRAFT);
      verify(dataStructureVersionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should block release when the stored model has no single root Element")
    void shouldBlockReleaseWhenModelHasNoSingleRoot() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setModelUrn(
          "urn:core:platform:civitas:datastructure:common:6f1c2a3e-8d4b-4c5a-9e7f-1a2b3c4d5e6f:1.0.0");

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      doThrow(new ModelRootMissingException())
          .when(modelRegistryGateway)
          .requireSingleRoot(version.getModelUrn());

      assertThatThrownBy(() -> dataStructureVersionService.release(versionId))
          .isInstanceOf(ModelRootMissingException.class);
      assertThat(version.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.DRAFT);
      verify(dataStructureVersionRepository, never()).save(any());
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
              any(),
              eq(Map.of("title", "Observation")),
              eq(Map.of("color", "blue")),
              any(),
              any());
      assertThat(result.getVersion()).isEqualTo("1.0.0");
      assertThat(result.getModelUrn())
          .isEqualTo(
              "urn:core:platform:civitas:element:common:7c96ac2d-1fad-817a-a9b0-c7374bba1952:1.0.0");
      assertThat(dataStructure.getModelLogicalUrn())
          .as("The parent's stable logical URN is minted on the first store")
          .isEqualTo(
              "urn:core:platform:civitas:element:common:00000000-0000-4000-8000-000000000016");
    }

    @Test
    @DisplayName("A version's first model starts a new major")
    void createStartsANewMajor() {
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dataStructureId);
      dataStructure.setModelLogicalUrn(
          "urn:core:platform:civitas:element:common:00000000-0000-4000-8000-000000000016");

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
              eq(
                  Optional.of(
                      "urn:core:platform:civitas:element:common:00000000-0000-4000-8000-000000000016")),
              any(),
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
      dataStructure.setModelLogicalUrn(
          "urn:core:platform:civitas:element:common:00000000-0000-4000-8000-000000000016");

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
          .storeModel(any(), any(), any(), any(), any(), eq(VersionBump.MAJOR), isNull());
    }

    @Test
    @DisplayName("Changing a model a version already has advances its minor")
    void editAdvancesTheMinor() {
      UUID versionId = UUID.randomUUID();
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dataStructureId);
      dataStructure.setModelLogicalUrn(
          "urn:core:platform:civitas:element:common:00000000-0000-4000-8000-000000000016");

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setVersion("2.4.0");
      version.setModelUrn(
          "urn:core:platform:civitas:element:common:7c96ac2d-1fad-817a-a9b0-c7374bba1952:2.4.0");
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
          .storeModel(any(), any(), any(), any(), any(), eq(VersionBump.MINOR), eq("2.4.0"));
    }

    @Test
    @DisplayName("Create without a model does not touch Model Forge and leaves the version unset")
    void createWithoutModelSkipsRegistry() {
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dataStructureId);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureId(dataStructureId);

      DataStructureVersion newEntity = new DataStructureVersion();

      when(dataStructureVersionMapper.toEntity(any())).thenReturn(newEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);
      when(dataStructureVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataStructureVersion result = dataStructureVersionService.create(input);

      verify(modelRegistryGateway, org.mockito.Mockito.never())
          .storeModel(any(), any(), any(), any(), any(), any(), any());
      assertThat(result.getVersion()).isNull();
      assertThat(result.getModelUrn()).isNull();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "<img src=x onerror=alert(1)>",
          "urn:core:platform:civitas:datastructure:frost:Things:k3j4h5g6f7:1.0.0",
          "urn:core:platform:civitas:element:common:7c96ac2d-1fad-817a-a9b0-c7374bba1952:1.0.0",
          "urn:core:platform:civitas:datastructure:frost:09846bf2-00d1-8ef2-bede-0e5936586b89"
        })
    @DisplayName("An imported structure that is not a pinned data structure URN is refused")
    void createWithInvalidImportedStructureIsRefused(String importedStructureUrn) {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureId(UUID.randomUUID());
      input.setModel(new HashMap<>(Map.of("title", "Observation")));
      input.setImportedStructureUrns(List.of(importedStructureUrn));
      when(dataStructureVersionMapper.toEntity(any())).thenReturn(new DataStructureVersion());

      assertThatThrownBy(() -> dataStructureVersionService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("importedStructureUrns");
      verifyNoInteractions(modelRegistryGateway);
    }

    @Test
    @DisplayName("An imported structure named by a pinned data structure URN is accepted")
    void createWithPinnedImportedStructureIsAccepted() {
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dataStructureId);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureId(dataStructureId);
      input.setModel(new HashMap<>(Map.of("title", "Observation")));
      input.setImportedStructureUrns(
          List.of(
              "urn:core:platform:civitas:datastructure:frost:09846bf2-00d1-8ef2-bede-0e5936586b89:1.0.0"));

      when(dataStructureVersionMapper.toEntity(any())).thenReturn(new DataStructureVersion());
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);
      when(dataStructureVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      assertThat(dataStructureVersionService.create(input).getVersion()).isEqualTo("1.0.0");
    }

    // Without the check the insert hits the unique constraint, and the 409 quotes the database.
    @Test
    @DisplayName("A new version whose model is unchanged from an existing version is refused")
    void createWithUnchangedModelIsRefused() {
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dataStructureId);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureId(dataStructureId);
      input.setModel(new HashMap<>(Map.of("title", "Observation")));

      when(dataStructureVersionMapper.toEntity(any())).thenReturn(new DataStructureVersion());
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);
      when(dataStructureVersionRepository.existsByDataStructureIdAndModelUrn(
              dataStructureId,
              "urn:core:platform:civitas:element:common:7c96ac2d-1fad-817a-a9b0-c7374bba1952:1.0.0"))
          .thenReturn(true);

      assertThatThrownBy(() -> dataStructureVersionService.create(input))
          .isInstanceOf(UniqueConstraintViolationException.class)
          .hasMessageContaining("unchanged from version 1.0.0");
      verify(dataStructureVersionRepository, never()).save(any());
    }

    private DataStructureVersion draftWithStoredModel(UUID versionId, DataStructure dataStructure) {
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setVersion("2.4.0");
      version.setModelUrn(STORED_PIN);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setDataStructure(dataStructure);
      return version;
    }

    private static final String STORED_PIN =
        "urn:core:platform:civitas:element:common:7c96ac2d-1fad-817a-a9b0-c7374bba1952:2.4.0";

    @Test
    @DisplayName("References are checked against the stored model before anything is stored")
    void referencesAreCheckedBeforeTheModelIsStored() {
      UUID versionId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(UUID.randomUUID());
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureId(dataStructure.getId());
      input.setModel(new HashMap<>(Map.of("title", "Replaced")));

      when(dataStructureVersionRepository.findById(versionId))
          .thenReturn(Optional.of(draftWithStoredModel(versionId, dataStructure)));
      when(dataStructureVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataStructureService.findByIdOrThrow(dataStructure.getId())).thenReturn(dataStructure);

      dataStructureVersionService.update(versionId, input);

      InOrder order = inOrder(modelReferenceAuthorizer, modelRegistryGateway);
      order
          .verify(modelReferenceAuthorizer)
          .authorizeWrite(dataStructure.getId(), input.getModel(), null, STORED_PIN);
      order
          .verify(modelRegistryGateway)
          .storeModel(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("A refused reference leaves nothing stored")
    void aRefusedReferenceLeavesNothingStored() {
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(UUID.randomUUID());
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureId(dataStructure.getId());
      input.setModel(new HashMap<>(Map.of("title", "Observation")));

      when(dataStructureVersionMapper.toEntity(any())).thenReturn(new DataStructureVersion());
      when(dataStructureService.findByIdOrThrow(dataStructure.getId())).thenReturn(dataStructure);
      doThrow(new ModelReferenceNotAvailableException())
          .when(modelReferenceAuthorizer)
          .authorizeWrite(eq(dataStructure.getId()), any(), any(), isNull());

      assertThatThrownBy(() -> dataStructureVersionService.create(input))
          .isInstanceOf(ModelReferenceNotAvailableException.class);
      verify(modelRegistryGateway, never())
          .storeModel(any(), any(), any(), any(), any(), any(), any());
      verify(dataStructureVersionRepository, never()).save(any());
    }

    @Test
    @DisplayName("An unchanged model stores nothing and keeps its pin")
    void anUnchangedModelStoresNothing() {
      UUID versionId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(UUID.randomUUID());
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureId(dataStructure.getId());
      input.setModel(new HashMap<>(Map.of("title", "Kept")));

      when(dataStructureVersionRepository.findById(versionId))
          .thenReturn(Optional.of(draftWithStoredModel(versionId, dataStructure)));
      when(dataStructureVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataStructureService.findByIdOrThrow(dataStructure.getId())).thenReturn(dataStructure);
      when(modelRegistryGateway.isUnchanged(eq(STORED_PIN), any(), any())).thenReturn(true);

      DataStructureVersion result = dataStructureVersionService.update(versionId, input);

      verify(modelRegistryGateway, never())
          .storeModel(any(), any(), any(), any(), any(), any(), any());
      assertThat(result.getModelUrn()).isEqualTo(STORED_PIN);
    }
  }

  @Nested
  @DisplayName("Duplicate")
  class DuplicateTests {

    private static final String SOURCE_PIN =
        "urn:core:platform:civitas:datastructure:common:6f1c2a3e-8d4b-4c5a-9e7f-1a2b3c4d5e6f:1.0.0";
    private static final ModelRegistryGateway.ModelPin COPY_PIN =
        new ModelRegistryGateway.ModelPin(
            "urn:core:platform:civitas:datastructure:common:6f1c2a3e-8d4b-4c5a-9e7f-1a2b3c4d5e6f",
            "urn:core:platform:civitas:datastructure:common:6f1c2a3e-8d4b-4c5a-9e7f-1a2b3c4d5e6f:3.0.0",
            "3.0.0");
    private static final String IMPORTED_STRUCTURE =
        "urn:core:platform:civitas:datastructure:frost:09846bf2-00d1-8ef2-bede-0e5936586b89:1.0.0";

    private final DataStructure dataStructure = new DataStructure();

    @BeforeEach
    void mapInputToEntity() {
      dataStructure.setId(UUID.randomUUID());
      lenient()
          .when(dataStructureVersionMapper.toEntity(any()))
          .thenAnswer(
              invocation -> {
                DataStructureVersionInputDTO input = invocation.getArgument(0);
                DataStructureVersion entity = new DataStructureVersion();
                entity.setDescription(input.getDescription());
                entity.setModelName(input.getModelName());
                entity.setImportedStructureUrns(input.getImportedStructureUrns());
                entity.setDataStructureVersionStatus(input.getDataStructureVersionStatus());
                return entity;
              });
      lenient()
          .when(dataStructureService.findByIdOrThrow(dataStructure.getId()))
          .thenReturn(dataStructure);
      lenient()
          .when(dataStructureVersionRepository.save(any()))
          .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("A copy takes the source's metadata, starts as DRAFT and pins the copied model")
    void duplicateCopiesMetadataAndPinsTheCopiedModel() {
      DataStructureVersion source = source(SOURCE_PIN);
      when(modelRegistryGateway.copyModelVersion(SOURCE_PIN)).thenReturn(COPY_PIN);

      DataStructureVersion copy = dataStructureVersionService.duplicate(source.getId());

      assertThat(copy.getDataStructureVersionStatus()).isEqualTo(DataStructureVersionStatus.DRAFT);
      assertThat(copy.getDescription()).isEqualTo("Weather stations");
      assertThat(copy.getModelName()).isEqualTo("Station");
      assertThat(copy.getImportedStructureUrns()).containsExactly(IMPORTED_STRUCTURE);
      assertThat(copy.getDataStructure()).isSameAs(dataStructure);
      assertThat(copy.getModelUrn()).isEqualTo(COPY_PIN.versionedUrn());
      assertThat(copy.getVersion()).isEqualTo("3.0.0");
      verify(modelRegistryGateway, never())
          .storeModel(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("A source without a model gives a copy without a model and no registry call")
    void duplicateWithoutModelSkipsRegistry() {
      DataStructureVersion source = source(null);

      DataStructureVersion copy = dataStructureVersionService.duplicate(source.getId());

      assertThat(copy.getModelUrn()).isNull();
      assertThat(copy.getVersion()).isNull();
      assertThat(copy.getDescription()).isEqualTo("Weather stations");
      verifyNoInteractions(modelRegistryGateway, modelReferenceAuthorizer);
    }

    @Test
    @DisplayName("A refused model reference copies nothing")
    void duplicateChecksReferencesBeforeTheCopy() {
      DataStructureVersion source = source(SOURCE_PIN);
      doThrow(new ModelReferenceNotAvailableException())
          .when(modelReferenceAuthorizer)
          .authorizeStored(dataStructure.getId(), SOURCE_PIN);

      assertThatThrownBy(() -> dataStructureVersionService.duplicate(source.getId()))
          .isInstanceOf(ModelReferenceNotAvailableException.class);
      verify(modelRegistryGateway, never()).copyModelVersion(any());
      verify(dataStructureVersionRepository, never()).save(any());
    }

    private DataStructureVersion source(String modelUrn) {
      DataStructureVersion source = new DataStructureVersion();
      source.setId(UUID.randomUUID());
      source.setDataStructure(dataStructure);
      source.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      source.setDescription("Weather stations");
      source.setModelName("Station");
      source.setModelUrn(modelUrn);
      source.setVersion(modelUrn == null ? null : "1.0.0");
      source.setImportedStructureUrns(List.of(IMPORTED_STRUCTURE));
      when(dataStructureVersionRepository.findById(source.getId())).thenReturn(Optional.of(source));
      return source;
    }
  }
}
