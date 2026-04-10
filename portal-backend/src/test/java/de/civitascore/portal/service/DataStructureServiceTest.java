package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.ResourceInUseException;
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
@DisplayName("DataStructureService Unit Tests")
class DataStructureServiceTest {

  @Mock private DataStructureRepository dataStructureRepository;
  @Mock private DataStructureMapper dataStructureMapper;
  @Mock private AssignmentRepository assignmentRepository;
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private DataSourceRepository dataSourceRepository;

  @InjectMocks private DataStructureService dataStructureService;

  @Nested
  @DisplayName("Unpublish inUse guard")
  class UnpublishInUseTests {

    @Test
    @DisplayName("Should block unpublish when any version is in use")
    void shouldBlockUnpublishWhenVersionInUse() {
      UUID dsId = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);

      DataStructure ds = new DataStructure();
      ds.setId(dsId);
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      ds.setDataStructureVersions(Set.of(version));

      when(dataStructureRepository.findById(dsId)).thenReturn(Optional.of(ds));
      when(dataSourceRepository.existsByDataStructureVersionIdIn(Set.of(versionId)))
          .thenReturn(true);

      assertThatThrownBy(() -> dataStructureService.unrelease(dsId))
          .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    @DisplayName("Should allow unpublish when no version is in use")
    void shouldAllowUnpublishWhenNoVersionInUse() {
      UUID dsId = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);

      DataStructure ds = new DataStructure();
      ds.setId(dsId);
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      ds.setDataStructureVersions(Set.of(version));

      when(dataStructureRepository.findById(dsId)).thenReturn(Optional.of(ds));
      when(dataSourceRepository.existsByDataStructureVersionIdIn(Set.of(versionId)))
          .thenReturn(false);
      when(dataStructureRepository.save(ds)).thenReturn(ds);

      dataStructureService.unrelease(dsId);

      verify(dataStructureRepository).save(ds);
    }
  }

  @Nested
  @DisplayName("Delete inUse guard")
  class DeleteInUseTests {

    @Test
    @DisplayName("Should block delete when any version is in use")
    void shouldBlockDeleteWhenVersionInUse() {
      UUID dsId = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);

      DataStructure ds = new DataStructure();
      ds.setId(dsId);
      ds.setDataStructureVersions(Set.of(version));

      when(dataStructureRepository.existsById(dsId)).thenReturn(true);
      when(dataStructureRepository.findById(dsId)).thenReturn(Optional.of(ds));
      when(dataSourceRepository.existsByDataStructureVersionIdIn(Set.of(versionId)))
          .thenReturn(true);

      assertThatThrownBy(() -> dataStructureService.deleteById(dsId))
          .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    @DisplayName("Should allow delete when no version is in use")
    void shouldAllowDeleteWhenNoVersionInUse() {
      UUID dsId = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);

      DataStructure ds = new DataStructure();
      ds.setId(dsId);
      ds.setDataStructureVersions(Set.of(version));

      when(dataStructureRepository.existsById(dsId)).thenReturn(true);
      when(dataStructureRepository.findById(dsId)).thenReturn(Optional.of(ds));
      when(dataSourceRepository.existsByDataStructureVersionIdIn(Set.of(versionId)))
          .thenReturn(false);

      dataStructureService.deleteById(dsId);

      verify(dataStructureRepository).deleteById(dsId);
    }
  }
}
