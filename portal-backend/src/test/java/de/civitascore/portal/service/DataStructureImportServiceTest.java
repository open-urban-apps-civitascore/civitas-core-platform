package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureImportInputDTO;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.util.InvalidInputException;
import java.util.Map;
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
 * split into the two create calls (field threading, forced defaults, ordering) — transactional
 * rollback is the container's job and is not unit-testable here.
 */
@ExtendWith(MockitoExtension.class)
class DataStructureImportServiceTest {

  @Mock private DataStructureService dataStructureService;
  @Mock private DataStructureVersionService dataStructureVersionService;
  @InjectMocks private DataStructureImportService importService;

  @Captor private ArgumentCaptor<DataStructureInputDTO> structureInputCaptor;
  @Captor private ArgumentCaptor<DataStructureVersionInputDTO> versionInputCaptor;

  private static DataStructureImportInputDTO importInput() {
    DataStructureImportInputDTO input = new DataStructureImportInputDTO();
    input.setName("Air Quality Station");
    input.setDescription("Structure description");
    input.setVersionDescription("Version description");
    input.setModelName("AirQualityStation");
    input.setModel(Map.of("$defs", Map.of()));
    input.setStyles(Map.of("nodes", Map.of()));
    return input;
  }

  @Test
  void importDataStructure_threadsFieldsIntoBothCreates() {
    UUID structureId = UUID.randomUUID();
    DataStructure structure = new DataStructure();
    structure.setId(structureId);
    when(dataStructureService.create(any(DataStructureInputDTO.class))).thenReturn(structure);
    DataStructureVersion version = new DataStructureVersion();
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

  @Test
  void importDataStructure_whenStructureCreateFails_neverCreatesVersion() {
    when(dataStructureService.create(any(DataStructureInputDTO.class)))
        .thenThrow(
            new InvalidInputException("DataStructure", "name", "Name cannot be null or blank"));

    assertThatThrownBy(() -> importService.importDataStructure(importInput()))
        .isInstanceOf(InvalidInputException.class);

    verify(dataStructureVersionService, never()).create(any());
  }
}
