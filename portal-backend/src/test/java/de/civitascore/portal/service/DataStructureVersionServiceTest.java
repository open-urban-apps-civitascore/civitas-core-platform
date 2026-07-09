package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.model.dataset.CoreUrn;
import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
  @Mock private DataSinkRepository dataSinkRepository;

  @InjectMocks private DataStructureVersionService dataStructureVersionService;

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
    @DisplayName("Should block unrelease when version is referenced by a DataSink")
    void shouldBlockUnreleaseWhenReferencedByDataSink() {
      UUID versionId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(false);
      when(dataSinkRepository.existsByDataStructureVersionId(versionId)).thenReturn(true);

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
    @DisplayName("Should block delete when version is referenced by a DataSink")
    void shouldBlockDeleteWhenReferencedByDataSink() {
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
      when(dataSinkRepository.existsByDataStructureVersionId(versionId)).thenReturn(true);

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
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setVersion("1.0.0");
      version.setModelName("OldModel");
      version.setModel(new HashMap<>(Map.of("title", "Old")));
      version.setStyles(new HashMap<>(Map.of("color", "blue")));
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setVersion("2.0.0");
      input.setModel(new HashMap<>(Map.of("title", "New")));
      input.setStyles(new HashMap<>(Map.of("color", "red")));

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(false);
      // Mock mapper does not update entity, so validateUniqueVersion sees the original "1.0.0"
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "1.0.0"))
          .thenReturn(Set.of(version));
      when(dataStructureVersionRepository.save(any())).thenReturn(version);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(ds);

      assertThatNoException()
          .isThrownBy(() -> dataStructureVersionService.updateReleasedMeta(versionId, input));
    }

    @Test
    @DisplayName("Should block model and structural changes via updateReleasedMeta when in use")
    void shouldBlockStructuralChangesWhenInUse() {
      UUID versionId = UUID.randomUUID();
      UUID dataStructureId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setId(dataStructureId);
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      Map<String, Object> originalModel = new HashMap<>(Map.of("title", "Original"));
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setVersion("1.0.0");
      version.setModelName("OldModel");
      version.setModel(originalModel);
      version.setStyles(new HashMap<>(Map.of("color", "blue")));
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setVersion("2.0.0");
      input.setModel(new HashMap<>(Map.of("title", "SHOULD_NOT_CHANGE")));
      input.setStyles(new HashMap<>(Map.of("color", "red")));
      input.setModelName("UpdatedModelName");

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(true);
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "1.0.0"))
          .thenReturn(Set.of(version));
      when(dataStructureVersionRepository.save(any())).thenReturn(version);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(ds);

      dataStructureVersionService.updateReleasedMeta(versionId, input);

      // After preProcessUpdateInput, in-use structural fields should be reverted
      assertThat(input.getModel())
          .as("Model should be reverted to original")
          .isEqualTo(originalModel);
      assertThat(input.getModel())
          .as("Reverted model must be a copy, not the managed entity's own map reference")
          .isNotSameAs(originalModel);
      assertThat(input.getVersion()).as("Version should be reverted").isEqualTo("1.0.0");
      assertThat(input.getStyles().get("color")).as("Styles should be reverted").isEqualTo("blue");
      assertThat(input.getModelName())
          .as("ModelName is editable while in use")
          .isEqualTo("UpdatedModelName");
    }

    @Test
    @DisplayName(
        "Should block structural changes via updateReleasedMeta when referenced by a DataSink")
    void shouldBlockStructuralChangesWhenReferencedByDataSink() {
      UUID versionId = UUID.randomUUID();
      UUID dataStructureId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setId(dataStructureId);
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      Map<String, Object> originalModel = new HashMap<>(Map.of("title", "Original"));
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setVersion("1.0.0");
      version.setModel(originalModel);
      version.setStyles(new HashMap<>(Map.of("color", "blue")));
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setVersion("2.0.0");
      input.setModel(new HashMap<>(Map.of("title", "SHOULD_NOT_CHANGE")));
      input.setStyles(new HashMap<>(Map.of("color", "red")));

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(false);
      when(dataSinkRepository.existsByDataStructureVersionId(versionId)).thenReturn(true);
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "1.0.0"))
          .thenReturn(Set.of(version));
      when(dataStructureVersionRepository.save(any())).thenReturn(version);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(ds);

      dataStructureVersionService.updateReleasedMeta(versionId, input);

      assertThat(input.getModel())
          .as("Model should be reverted when a DataSink references the version")
          .isEqualTo(originalModel);
      assertThat(input.getVersion()).as("Version should be reverted").isEqualTo("1.0.0");
      assertThat(input.getStyles().get("color")).as("Styles should be reverted").isEqualTo("blue");
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
      input.setVersion("1.0.0");

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
      version.setModel(new HashMap<>(Map.of("title", "Existing")));
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setVersion("1.0.0");
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
    @DisplayName("Should block release when version has no model")
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
    @DisplayName("Should block release when version model is empty")
    void shouldBlockReleaseWhenModelIsEmpty() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setModel(new HashMap<>());

      when(dataStructureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

      assertThatThrownBy(() -> dataStructureVersionService.release(versionId))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("model");
    }

    @Test
    @DisplayName("Should allow release when version carries a model")
    void shouldAllowReleaseWhenModelPresent() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setModel(new HashMap<>(Map.of("title", "Observation")));

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
  @DisplayName("Unique version validation (preSave guard)")
  class UniqueVersionTests {

    private DataStructure buildDataStructure(UUID dsId) {
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dsId);
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      return dataStructure;
    }

    /**
     * Build a minimal input DTO to satisfy preProcessCreateInput & postConvertToEntity.
     * dataStructureId is set via @JsonIgnore field (service layer path).
     */
    private DataStructureVersionInputDTO buildInput(UUID dataStructureId, String version) {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion(version);
      input.setDataStructureId(dataStructureId);
      return input;
    }

    @Test
    @DisplayName(
        "Should throw UniqueConstraintViolationException on create when version already exists for the same DataStructure")
    void shouldRejectDuplicateVersionOnCreate() {
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = buildDataStructure(dataStructureId);

      DataStructureVersionInputDTO input = buildInput(dataStructureId, "1.0.0");

      // The new entity produced by the mapper (id is null until saved)
      DataStructureVersion newEntity = new DataStructureVersion();
      newEntity.setVersion("1.0.0");
      newEntity.setDataStructureVersionSource(DataStructureVersionSource.OWN);

      // Existing entity already stored with the same version in the same DataStructure
      DataStructureVersion conflictingEntity = new DataStructureVersion();
      conflictingEntity.setId(UUID.randomUUID());
      conflictingEntity.setVersion("1.0.0");
      conflictingEntity.setDataStructure(dataStructure);

      when(dataStructureVersionMapper.toEntity(any())).thenReturn(newEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "1.0.0"))
          .thenReturn(Set.of(conflictingEntity));

      assertThatThrownBy(() -> dataStructureVersionService.create(input))
          .isInstanceOf(UniqueConstraintViolationException.class)
          .hasMessageContaining("Version must be unique");
    }

    @Test
    @DisplayName(
        "Should throw UniqueConstraintViolationException on update when version string is already used by another version in the same DataStructure")
    void shouldRejectDuplicateVersionOnUpdate() {
      UUID dataStructureId = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();
      UUID conflictingVersionId = UUID.randomUUID();
      DataStructure dataStructure = buildDataStructure(dataStructureId);

      DataStructureVersion existingEntity = new DataStructureVersion();
      existingEntity.setId(versionId);
      existingEntity.setVersion("1.0.0");
      existingEntity.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      existingEntity.setDataStructure(dataStructure);

      DataStructureVersion conflictingEntity = new DataStructureVersion();
      conflictingEntity.setId(conflictingVersionId);
      conflictingEntity.setVersion("2.0.0");
      conflictingEntity.setDataStructure(dataStructure);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setVersion("2.0.0"); // version already owned by conflictingEntity

      doAnswer(
              invocation -> {
                DataStructureVersion entity = invocation.getArgument(0);
                DataStructureVersionInputDTO dto = invocation.getArgument(1);
                entity.setVersion(dto.getVersion());
                return null;
              })
          .when(dataStructureVersionMapper)
          .updateEntity(any(), any());
      when(dataStructureVersionRepository.findById(versionId))
          .thenReturn(Optional.of(existingEntity));
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "2.0.0"))
          .thenReturn(Set.of(conflictingEntity));
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);

      assertThatThrownBy(() -> dataStructureVersionService.update(versionId, input))
          .isInstanceOf(UniqueConstraintViolationException.class)
          .hasMessageContaining("Version must be unique")
          .hasMessageContaining(conflictingVersionId.toString());
    }

    @Test
    @DisplayName(
        "Should not throw when no other version shares the same version string (unique create)")
    void shouldAllowCreateWhenVersionIsUnique() {
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = buildDataStructure(dataStructureId);

      DataStructureVersionInputDTO input = buildInput(dataStructureId, "3.0.0");

      DataStructureVersion newEntity = new DataStructureVersion();
      newEntity.setVersion("3.0.0");
      newEntity.setDataStructureVersionSource(DataStructureVersionSource.OWN);

      when(dataStructureVersionMapper.toEntity(any())).thenReturn(newEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "3.0.0"))
          .thenReturn(Set.of());
      when(dataStructureVersionRepository.save(any())).thenReturn(newEntity);

      assertThatNoException().isThrownBy(() -> dataStructureVersionService.create(input));
      verify(dataStructureVersionRepository)
          .findAllByDataStructureIdAndVersion(dataStructureId, "3.0.0");
    }

    @Test
    @DisplayName(
        "Should allow update when the only matching version is the entity itself (self-assignment)")
    void shouldAllowUpdateWithSameVersionString() {
      UUID dataStructureId = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();
      DataStructure dataStructure = buildDataStructure(dataStructureId);

      DataStructureVersion existingEntity = new DataStructureVersion();
      existingEntity.setId(versionId);
      existingEntity.setVersion("1.0.0");
      existingEntity.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      existingEntity.setDataStructure(dataStructure);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setVersion("1.0.0"); // same as existing — self-assignment

      // Only match is the entity being updated itself
      when(dataStructureVersionRepository.findById(versionId))
          .thenReturn(Optional.of(existingEntity));
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "1.0.0"))
          .thenReturn(Set.of(existingEntity));
      when(dataStructureVersionRepository.save(any())).thenReturn(existingEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);

      assertThatNoException()
          .isThrownBy(() -> dataStructureVersionService.update(versionId, input));
      verify(dataStructureVersionRepository)
          .findAllByDataStructureIdAndVersion(dataStructureId, "1.0.0");
    }
  }

  @Nested
  @DisplayName("Release model $id validation")
  class ReleaseModelIdTests {

    private static final UUID DATA_STRUCTURE_ID =
        UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    // disambiguator 2dmtus8w40 is CoreUrn.disambiguatorFor(DATA_STRUCTURE_ID)
    private static final String VALID_URN =
        "urn:core:platform:civitas:datastructure:common:WeatherModel:2dmtus8w40:1.0.0";

    private DataStructureVersion draftVersionWithModel(Map<String, Object> model) {
      DataStructure ds = new DataStructure();
      ds.setId(DATA_STRUCTURE_ID);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(UUID.randomUUID());
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setDataStructure(ds);
      version.setModel(model);
      when(dataStructureVersionRepository.findById(version.getId()))
          .thenReturn(Optional.of(version));
      return version;
    }

    @Test
    @DisplayName("releases when the model carries no $id")
    void releasesWithoutModelId() {
      DataStructureVersion version = draftVersionWithModel(new HashMap<>(Map.of("type", "object")));
      when(dataStructureVersionRepository.save(version)).thenReturn(version);

      dataStructureVersionService.release(version.getId());

      assertThat(version.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
    }

    @Test
    @DisplayName("releases when the $id disambiguator is derived from the DataStructure id")
    void releasesWithMatchingModelId() {
      DataStructureVersion version = draftVersionWithModel(new HashMap<>(Map.of("$id", VALID_URN)));
      when(dataStructureVersionRepository.save(version)).thenReturn(version);

      dataStructureVersionService.release(version.getId());

      assertThat(version.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
    }

    @Test
    @DisplayName("rejection message does not reveal the expected DataStructure id")
    void rejectionDoesNotRevealDataStructureId() {
      DataStructureVersion version =
          draftVersionWithModel(
              new HashMap<>(
                  Map.of(
                      "$id",
                      "urn:core:platform:civitas:datastructure:common:WeatherModel:0000000001:1.0.0")));

      assertThatThrownBy(() -> dataStructureVersionService.release(version.getId()))
          .isInstanceOf(InvalidInputException.class)
          // anchor to the real rejection so the negative assertions cannot pass vacuously
          .hasMessageContaining("not a valid CORE URN")
          .hasMessageNotContaining(DATA_STRUCTURE_ID.toString())
          .hasMessageNotContaining(CoreUrn.disambiguatorFor(DATA_STRUCTURE_ID));
    }

    @Test
    @DisplayName("rejects a malformed $id")
    void rejectsMalformedModelId() {
      DataStructureVersion version =
          draftVersionWithModel(new HashMap<>(Map.of("$id", "not-a-core-urn")));

      assertThatThrownBy(() -> dataStructureVersionService.release(version.getId()))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("rejects an $id whose disambiguator belongs to another DataStructure")
    void rejectsForeignDisambiguator() {
      DataStructureVersion version =
          draftVersionWithModel(
              new HashMap<>(
                  Map.of(
                      "$id",
                      "urn:core:platform:civitas:datastructure:common:WeatherModel:0000000001:1.0.0")));

      assertThatThrownBy(() -> dataStructureVersionService.release(version.getId()))
          .isInstanceOf(InvalidInputException.class);
    }
  }
}
