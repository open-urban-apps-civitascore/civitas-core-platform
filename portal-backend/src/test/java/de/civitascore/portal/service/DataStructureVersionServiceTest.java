package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
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
  @Mock private ModelService modelService;
  @Mock private DataSourceRepository dataSourceRepository;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private DataStructureVersionService dataStructureVersionService;

  @Nested
  @DisplayName("Unpublish inUse guard")
  class UnpublishInUseTests {

    @Test
    @DisplayName("Should block unpublish when version is in use by a DataSource")
    void shouldBlockUnpublishWhenInUse() {
      UUID versionId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(true);

      assertThatThrownBy(() -> dataStructureVersionService.unpublish(versionId))
          .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    @DisplayName("Should allow unpublish when version is not in use")
    void shouldAllowUnpublishWhenNotInUse() {
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

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(false);
      when(dataStructureVersionRepository.save(version)).thenReturn(version);

      dataStructureVersionService.unpublish(versionId);

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

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.existsById(versionId)).thenReturn(true);
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(true);

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

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.existsById(versionId)).thenReturn(true);
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(false);

      dataStructureVersionService.deleteById(versionId);

      verify(dataStructureVersionRepository).deleteById(versionId);
    }
  }

  @Nested
  @DisplayName("UpdatePublishedMeta inUse guard")
  class UpdatePublishedMetaInUseTests {

    @Test
    @DisplayName("Should allow full update via updatePublishedMeta when version is not in use")
    void shouldAllowFullUpdateWhenNotInUse() {
      UUID versionId = UUID.randomUUID();
      UUID dataStructureId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setId(dataStructureId);
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setVersion("1.0.0");
      version.setModelAtlasUri("https://modelatlas.example.com/old");
      version.setModelName("OldModel");
      version.setStyles(new HashMap<>(Map.of("color", "blue")));
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setVersion("2.0.0");
      input.setModelAtlasUri("https://modelatlas.example.com/new");
      input.setModel("<xml>new model</xml>");
      input.setStyles(new HashMap<>(Map.of("color", "red")));

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(false);
      // Mock mapper does not update entity, so validateUniqueVersion sees the original "1.0.0"
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "1.0.0"))
          .thenReturn(Set.of(version));
      when(dataStructureVersionRepository.save(any())).thenReturn(version);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(ds);

      assertThatNoException()
          .isThrownBy(() -> dataStructureVersionService.updatePublishedMeta(versionId, input));
    }

    @Test
    @DisplayName(
        "Should block model and structural changes via updatePublishedMeta when version is in use")
    void shouldBlockStructuralChangesWhenInUse() {
      UUID versionId = UUID.randomUUID();
      UUID dataStructureId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setId(dataStructureId);
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setVersion("1.0.0");
      version.setModelAtlasUri("https://modelatlas.example.com/original");
      version.setModelName("OldModel");
      version.setStyles(new HashMap<>(Map.of("color", "blue")));
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructure(ds);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setVersion("2.0.0");
      input.setModelAtlasUri("https://modelatlas.example.com/SHOULD_NOT_CHANGE");
      input.setModel("<xml>should not upload</xml>");
      input.setStyles(new HashMap<>(Map.of("color", "red")));
      input.setModelName("UpdatedModelName");

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(true);
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "1.0.0"))
          .thenReturn(Set.of(version));
      when(dataStructureVersionRepository.save(any())).thenReturn(version);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(ds);

      dataStructureVersionService.updatePublishedMeta(versionId, input);

      // After preProcessUpdateInput, in-use fields should be reverted
      assertThat(input.getModelAtlasUri())
          .as("ModelAtlasUri should be reverted to original")
          .isEqualTo("https://modelatlas.example.com/original");
      assertThat(input.getModel()).as("Model should be cleared").isNull();
      assertThat(input.getVersion()).as("Version should be reverted").isEqualTo("1.0.0");
      assertThat(input.getStyles().get("color")).as("Styles should be reverted").isEqualTo("blue");
    }

    @Test
    @DisplayName("Should reject updatePublishedMeta for DRAFT version")
    void shouldRejectUpdatePublishedMetaForDraftVersion() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setVersion("1.0.0");

      assertThatThrownBy(() -> dataStructureVersionService.updatePublishedMeta(versionId, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DRAFT");
    }
  }

  @Nested
  @DisplayName("Publish model existence guard")
  class PublishModelExistenceTests {

    @Test
    @DisplayName(
        "Should block publish when modelAtlasUri is set but no model exists in Model Atlas")
    void shouldBlockPublishWhenModelNotFoundInAtlas() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setModelAtlasUri("http://example.com/model/missing");

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));
      when(modelService.downloadModel("http://example.com/model/missing", "application/xml"))
          .thenThrow(new RuntimeException("Not found"));

      assertThatThrownBy(() -> dataStructureVersionService.publish(versionId))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("no model found in Model Atlas");
    }

    @Test
    @DisplayName("Should block publish when Model Atlas returns null content")
    void shouldBlockPublishWhenModelContentIsNull() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setModelAtlasUri("http://example.com/model/empty");

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));
      when(modelService.downloadModel("http://example.com/model/empty", "application/xml"))
          .thenReturn(null);

      assertThatThrownBy(() -> dataStructureVersionService.publish(versionId))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("no model found in Model Atlas");
    }

    @Test
    @DisplayName("Should allow publish when model exists in Model Atlas")
    void shouldAllowPublishWhenModelExists() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setModelAtlasUri("http://example.com/model/valid");

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));
      when(modelService.downloadModel("http://example.com/model/valid", "application/xml"))
          .thenReturn("<xml>model content</xml>");
      when(dataStructureVersionRepository.save(version)).thenReturn(version);

      DataStructureVersion result = dataStructureVersionService.publish(versionId);

      assertThat(result.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
      verify(dataStructureVersionRepository).save(version);
    }

    @Test
    @DisplayName("Should block publish when modelAtlasUri is blank")
    void shouldBlockPublishWhenModelAtlasUriIsBlank() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setModelAtlasUri("  ");

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));

      assertThatThrownBy(() -> dataStructureVersionService.publish(versionId))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("modelAtlasUri");
    }

    @Test
    @DisplayName("Should block publish when version is already published")
    void shouldBlockPublishWhenAlreadyPublished() {
      UUID versionId = UUID.randomUUID();
      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));

      assertThatThrownBy(() -> dataStructureVersionService.publish(versionId))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("already published");
    }
  }

  @Nested
  @DisplayName("findModelByAtlasUri")
  class FindModelByAtlasUriTests {

    @Test
    @DisplayName("Should return model content when modelAtlasUri is present and download succeeds")
    void shouldReturnModelWhenUriPresentAndDownloadSucceeds() {
      String expectedModel = "<?xml version=\"1.0\"?><model>content</model>";
      when(modelService.downloadModel("http://example.com/model/1.0.0", "application/xml"))
          .thenReturn(expectedModel);

      Optional<String> result =
          dataStructureVersionService.findModelByAtlasUri("http://example.com/model/1.0.0");

      assertThat(result).isPresent().contains(expectedModel);
      verify(modelService).downloadModel("http://example.com/model/1.0.0", "application/xml");
    }

    @Test
    @DisplayName("Should return empty when modelAtlasUri is blank")
    void shouldReturnEmptyWhenUriIsBlank() {
      Optional<String> result = dataStructureVersionService.findModelByAtlasUri("  ");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when modelAtlasUri is null")
    void shouldReturnEmptyWhenUriIsNull() {
      Optional<String> result = dataStructureVersionService.findModelByAtlasUri(null);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when download fails")
    void shouldReturnEmptyWhenDownloadFails() {
      when(modelService.downloadModel("http://example.com/model/1.0.0", "application/xml"))
          .thenThrow(new RuntimeException("Connection refused"));

      Optional<String> result =
          dataStructureVersionService.findModelByAtlasUri("http://example.com/model/1.0.0");

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Model upload with special characters")
  class ModelUploadUmlautTests {

    @Test
    @DisplayName("Should pass German umlauts through to ModelService without corruption")
    void shouldPreserveUmlautsOnCreate() {
      UUID dataStructureId = UUID.randomUUID();
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(dataStructureId);
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);

      String modelWithUmlauts = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><model>äöüÄÖÜß</model>";
      String nsUri = "http://example.com/model";

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("1.0.0");
      input.setDataStructureId(dataStructureId);
      input.setModel(modelWithUmlauts);
      input.setModelAtlasUri(nsUri);

      DataStructureVersion newEntity = new DataStructureVersion();
      newEntity.setVersion("1.0.0");
      newEntity.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      newEntity.setModelAtlasUri(nsUri);

      when(dataStructureVersionMapper.toEntity(any())).thenReturn(newEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "1.0.0"))
          .thenReturn(Set.of());
      when(dataStructureVersionRepository.save(any())).thenReturn(newEntity);

      dataStructureVersionService.create(input);

      verify(modelService).uploadModelString(eq(modelWithUmlauts), eq(nsUri));
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
      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
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
    @DisplayName("Should allow update when modelAtlasUri is present but model is null (PATCH fix)")
    void shouldAllowUpdateWithModelAtlasUriButNoModel() {
      UUID dataStructureId = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();
      DataStructure dataStructure = buildDataStructure(dataStructureId);

      DataStructureVersion existingEntity = new DataStructureVersion();
      existingEntity.setId(versionId);
      existingEntity.setVersion("1.0.0");
      existingEntity.setModelAtlasUri("https://modelatlas.example.com/model1");
      existingEntity.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      existingEntity.setDataStructure(dataStructure);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setVersion("1.0.0");
      input.setModelAtlasUri("https://modelatlas.example.com/model1");
      // model is null — simulates a PATCH that only changes description

      doAnswer(
              invocation -> {
                DataStructureVersion entity = invocation.getArgument(0);
                DataStructureVersionInputDTO dto = invocation.getArgument(1);
                entity.setVersion(dto.getVersion());
                return null;
              })
          .when(dataStructureVersionMapper)
          .updateEntity(any(), any());
      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(existingEntity));
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "1.0.0"))
          .thenReturn(Set.of(existingEntity));
      when(dataStructureVersionRepository.save(any())).thenReturn(existingEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(dataStructure);

      assertThatNoException()
          .isThrownBy(() -> dataStructureVersionService.update(versionId, input));
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
      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
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
  @DisplayName("Model Atlas delete on version delete")
  class DeleteModelAtlasTests {

    @Test
    @DisplayName("Should delete model from Model Atlas when version with modelAtlasUri is deleted")
    void shouldDeleteModelFromAtlasOnVersionDelete() {
      UUID versionId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.DRAFT);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setModelAtlasUri("https://modelatlas.example.com/model1");
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setDataStructure(ds);

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.existsById(versionId)).thenReturn(true);
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(false);

      dataStructureVersionService.deleteById(versionId);

      verify(modelService).deleteModel("https://modelatlas.example.com/model1");
    }

    @Test
    @DisplayName("Should not call delete on Model Atlas when version has no modelAtlasUri")
    void shouldNotDeleteFromAtlasWhenNoUri() {
      UUID versionId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.DRAFT);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setDataStructure(ds);

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.existsById(versionId)).thenReturn(true);
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(false);

      dataStructureVersionService.deleteById(versionId);

      verify(modelService, never()).deleteModel(any());
    }

    @Test
    @DisplayName("Should still delete version even if Model Atlas delete fails")
    void shouldStillDeleteVersionWhenAtlasDeleteFails() {
      UUID versionId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.DRAFT);

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setModelAtlasUri("https://modelatlas.example.com/model1");
      version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version.setDataStructure(ds);

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(version));
      when(dataStructureVersionRepository.existsById(versionId)).thenReturn(true);
      when(dataSourceRepository.existsByDataStructureVersionId(versionId)).thenReturn(false);
      doThrow(new RuntimeException("Atlas down"))
          .when(modelService)
          .deleteModel("https://modelatlas.example.com/model1");

      assertThatNoException().isThrownBy(() -> dataStructureVersionService.deleteById(versionId));
      verify(dataStructureVersionRepository).deleteById(versionId);
    }
  }

  @Nested
  @DisplayName("Model Atlas URI change on update")
  class UriChangeOnUpdateTests {

    @Test
    @DisplayName("Should delete old model from Atlas when modelAtlasUri changes on update")
    void shouldDeleteOldModelWhenUriChanges() {
      UUID versionId = UUID.randomUUID();
      UUID dataStructureId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setId(dataStructureId);
      ds.setDataStructureStatus(DataStructureStatus.DRAFT);

      DataStructureVersion existingEntity = new DataStructureVersion();
      existingEntity.setId(versionId);
      existingEntity.setVersion("1.0.0");
      existingEntity.setModelAtlasUri("https://modelatlas.example.com/old-uri");
      existingEntity.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      existingEntity.setDataStructure(ds);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setVersion("1.0.0");
      input.setModelAtlasUri("https://modelatlas.example.com/new-uri");
      input.setModel("<xml>new model</xml>");

      String uploadResponse = "{\"objectId\":\"dGVzdE9iamVjdElk\",\"objectName\":\"TestModel\"}";

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(existingEntity));
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "1.0.0"))
          .thenReturn(Set.of(existingEntity));
      when(dataStructureVersionRepository.save(any())).thenReturn(existingEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(ds);
      when(modelService.uploadModelString(
              "<xml>new model</xml>", "https://modelatlas.example.com/new-uri"))
          .thenReturn(uploadResponse);

      dataStructureVersionService.update(versionId, input);

      verify(modelService).deleteModel("https://modelatlas.example.com/old-uri");
      verify(modelService)
          .uploadModelString("<xml>new model</xml>", "https://modelatlas.example.com/new-uri");
    }

    @Test
    @DisplayName("Should not delete old model when modelAtlasUri stays the same")
    void shouldNotDeleteWhenUriUnchanged() {
      UUID versionId = UUID.randomUUID();
      UUID dataStructureId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setId(dataStructureId);
      ds.setDataStructureStatus(DataStructureStatus.DRAFT);

      DataStructureVersion existingEntity = new DataStructureVersion();
      existingEntity.setId(versionId);
      existingEntity.setVersion("1.0.0");
      existingEntity.setModelAtlasUri("https://modelatlas.example.com/same-uri");
      existingEntity.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      existingEntity.setDataStructure(ds);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setVersion("1.0.0");
      input.setModelAtlasUri("https://modelatlas.example.com/same-uri");
      input.setModel("<xml>updated model</xml>");

      String uploadResponse = "{\"objectId\":\"dGVzdE9iamVjdElk\",\"objectName\":\"TestModel\"}";

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(existingEntity));
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "1.0.0"))
          .thenReturn(Set.of(existingEntity));
      when(dataStructureVersionRepository.save(any())).thenReturn(existingEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(ds);
      when(modelService.uploadModelString(
              "<xml>updated model</xml>", "https://modelatlas.example.com/same-uri"))
          .thenReturn(uploadResponse);

      dataStructureVersionService.update(versionId, input);

      verify(modelService, never()).deleteModel(any());
      verify(modelService)
          .uploadModelString("<xml>updated model</xml>", "https://modelatlas.example.com/same-uri");
    }

    @Test
    @DisplayName("Should parse and save externalId from upload response")
    void shouldParseExternalIdFromUploadResponse() throws Exception {
      UUID versionId = UUID.randomUUID();
      UUID dataStructureId = UUID.randomUUID();
      DataStructure ds = new DataStructure();
      ds.setId(dataStructureId);
      ds.setDataStructureStatus(DataStructureStatus.DRAFT);

      DataStructureVersion existingEntity = new DataStructureVersion();
      existingEntity.setId(versionId);
      existingEntity.setVersion("1.0.0");
      existingEntity.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      existingEntity.setDataStructure(ds);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setDataStructureId(dataStructureId);
      input.setVersion("1.0.0");
      input.setModelAtlasUri("https://modelatlas.example.com/model1");
      input.setModel("<xml>model</xml>");

      String uploadResponse =
          "{\"objectId\":\"aHR0cDovL3Rlc3QvbW9kZWw=\",\"objectName\":\"TestModel\"}";

      when(dataStructureVersionRepository.findByIdWithRelations(versionId))
          .thenReturn(Optional.of(existingEntity));
      when(dataStructureVersionRepository.findAllByDataStructureIdAndVersion(
              dataStructureId, "1.0.0"))
          .thenReturn(Set.of(existingEntity));
      when(dataStructureVersionRepository.save(any())).thenReturn(existingEntity);
      when(dataStructureService.findByIdOrThrow(dataStructureId)).thenReturn(ds);
      when(modelService.uploadModelString(
              "<xml>model</xml>", "https://modelatlas.example.com/model1"))
          .thenReturn(uploadResponse);

      ObjectMapper realMapper = new ObjectMapper();
      JsonNode rootNode = realMapper.readTree(uploadResponse);
      when(objectMapper.readTree(uploadResponse)).thenReturn(rootNode);

      dataStructureVersionService.update(versionId, input);

      assertThat(existingEntity.getExternalId()).isEqualTo("aHR0cDovL3Rlc3QvbW9kZWw=");
    }
  }
}
