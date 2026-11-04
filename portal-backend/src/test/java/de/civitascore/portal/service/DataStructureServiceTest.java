package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.List;
import java.util.Optional;
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
@DisplayName("DataStructureService Unit Tests")
class DataStructureServiceTest {

  private static final String MODEL_LOGICAL_URN =
      "urn:core:platform:civitas:element:common:e5eaeb37-4c14-8bb9-903b-4f86897bfcea";

  @Mock private DataStructureRepository dataStructureRepository;
  @Mock private DataStructureMapper dataStructureMapper;
  @Mock private AssignmentRepository assignmentRepository;
  @Mock private ArtifactUsageLookup artifactUsageLookup;
  @Mock private ModelRegistryGateway modelRegistryGateway;

  @InjectMocks private DataStructureService dataStructureService;

  @Nested
  @DisplayName("Delete inUse guard")
  class DeleteInUseTests {

    private final UUID dsId = UUID.randomUUID();
    private final DataStructure ds = new DataStructure();

    @BeforeEach
    void storedStructure() {
      ds.setId(dsId);
      ds.setModelLogicalUrn(MODEL_LOGICAL_URN);
      when(dataStructureRepository.existsById(dsId)).thenReturn(true);
      when(dataStructureRepository.findById(dsId)).thenReturn(Optional.of(ds));
    }

    @Test
    @DisplayName("Should block delete and keep the model when the usage lookup reports a use")
    void deleteById_whenAVersionIsInUse_shouldThrowAndKeepTheModel() {
      when(artifactUsageLookup.of(ds))
          .thenReturn(new ArtifactUsageLookup.ArtifactUsage(true, List.of(), List.of()));

      assertThatThrownBy(() -> dataStructureService.deleteById(dsId))
          .isInstanceOf(ResourceInUseException.class);
      verify(dataStructureRepository, never()).deleteById(any());
      verify(modelRegistryGateway, never()).deleteModel(any());
    }

    @Test
    @DisplayName("Should delete the structure and its model when nothing uses it")
    void deleteById_whenNothingIsInUse_shouldDeleteTheModel() {
      when(artifactUsageLookup.of(ds))
          .thenReturn(new ArtifactUsageLookup.ArtifactUsage(false, List.of(), List.of()));

      dataStructureService.deleteById(dsId);

      verify(dataStructureRepository).deleteById(dsId);
      verify(modelRegistryGateway).deleteModel(MODEL_LOGICAL_URN);
    }
  }
}
