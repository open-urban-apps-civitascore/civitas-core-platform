package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
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
}
