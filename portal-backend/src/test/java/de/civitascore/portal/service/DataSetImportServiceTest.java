package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataSetImportInputDTO;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSourceImportInputDTO;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataStructureImportInputDTO;
import de.civitascore.portal.model.output.DataSetImportOutputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.service.DataStructureImportService.ImportResolution;
import de.civitascore.portal.util.InvalidInputException;
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
 * Unit tests for {@link DataSetImportService}: orchestration order (structures → sources →
 * dataset), URN-based structure resolution for sources (bundle first, then installed), and the
 * explicit rejection of not-yet-supported bundle parts. The collaborating services are mocked;
 * their own guards are covered in {@link DataStructureImportServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class DataSetImportServiceTest {

  private static final String STRUCTURE_URN =
      "urn:core:city:openurbanapps:datastructure:environment:airqualitystation:default";
  private static final String VERSIONED_URN = STRUCTURE_URN + ":1.0.0";

  @Mock private DataStructureImportService dataStructureImportService;
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private DataSourceService dataSourceService;
  @Mock private DataSetService dataSetService;
  @InjectMocks private DataSetImportService importService;

  @Captor private ArgumentCaptor<DataSourceInputDTO> sourceInputCaptor;
  @Captor private ArgumentCaptor<DataSetInputDTO> dataSetInputCaptor;

  private static DataStructureImportInputDTO structureInput() {
    DataStructureImportInputDTO structure = new DataStructureImportInputDTO();
    structure.setName("Air Quality Station");
    structure.setModel(Map.of("$id", STRUCTURE_URN, "$defs", Map.of()));
    return structure;
  }

  private static DataSourceImportInputDTO sourceInput(String structureUrn) {
    DataSourceImportInputDTO source = new DataSourceImportInputDTO();
    source.setName("Station Feed");
    source.setDataStructureUrn(structureUrn);
    return source;
  }

  private static DataSetImportInputDTO bundle(
      List<DataStructureImportInputDTO> structures, List<DataSourceImportInputDTO> sources) {
    DataSetImportInputDTO input = new DataSetImportInputDTO();
    input.setName("Air Quality");
    input.setDescription("Bundle description");
    input.setDataStructures(structures);
    input.setDataSources(sources);
    return input;
  }

  private DataStructureVersion version() {
    DataStructure structure = new DataStructure();
    structure.setId(UUID.randomUUID());
    DataStructureVersion version = new DataStructureVersion();
    version.setId(UUID.randomUUID());
    version.setModelUrn(VERSIONED_URN);
    version.setDataStructure(structure);
    return version;
  }

  private void stubUrnHelpers() {
    when(modelRegistryGateway.isDataStructureUrn(STRUCTURE_URN)).thenReturn(true);
    when(modelRegistryGateway.logicalUrn(STRUCTURE_URN)).thenReturn(STRUCTURE_URN);
    when(modelRegistryGateway.logicalUrn(VERSIONED_URN)).thenReturn(STRUCTURE_URN);
  }

  @Test
  void importDataSet_wiresBundledStructureIntoSourceAndCreatesDataSetLast() {
    stubUrnHelpers();
    DataStructureVersion version = version();
    when(dataStructureImportService.importOrReuse(any(DataStructureImportInputDTO.class)))
        .thenReturn(new ImportResolution(version, false));
    DataSource createdSource = new DataSource();
    createdSource.setId(UUID.randomUUID());
    createdSource.setName("Station Feed");
    when(dataSourceService.create(any(DataSourceInputDTO.class))).thenReturn(createdSource);
    DataSet dataSet = new DataSet();
    dataSet.setId(UUID.randomUUID());
    dataSet.setName("Air Quality");
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(dataSet);

    DataSetImportOutputDTO output =
        importService.importDataSet(
            bundle(List.of(structureInput()), List.of(sourceInput(STRUCTURE_URN))));

    verify(dataSourceService).create(sourceInputCaptor.capture());
    assertThat(sourceInputCaptor.getValue().getDataStructureVersionId()).isEqualTo(version.getId());
    verify(dataSetService).create(dataSetInputCaptor.capture());
    assertThat(dataSetInputCaptor.getValue().getName()).isEqualTo("Air Quality");

    assertThat(output.getDataSetId()).isEqualTo(dataSet.getId());
    assertThat(output.getDataStructures()).hasSize(1);
    assertThat(output.getDataStructures().getFirst().getAction()).isEqualTo("CREATED");
    assertThat(output.getDataSources()).hasSize(1);
  }

  @Test
  void importDataSet_resolvesSourceReferenceAgainstInstalledStructure() {
    stubUrnHelpers();
    DataStructureVersion installed = version();
    when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(STRUCTURE_URN + ":"))
        .thenReturn(Optional.of(installed));
    DataSource createdSource = new DataSource();
    createdSource.setId(UUID.randomUUID());
    createdSource.setName("Station Feed");
    when(dataSourceService.create(any(DataSourceInputDTO.class))).thenReturn(createdSource);
    when(dataSetService.create(any(DataSetInputDTO.class))).thenReturn(new DataSet());

    importService.importDataSet(bundle(List.of(), List.of(sourceInput(STRUCTURE_URN))));

    verify(dataSourceService).create(sourceInputCaptor.capture());
    assertThat(sourceInputCaptor.getValue().getDataStructureVersionId())
        .isEqualTo(installed.getId());
  }

  @Test
  void importDataSet_whenSourceReferenceUnresolvable_rejectsBeforeCreatingAnything() {
    when(modelRegistryGateway.isDataStructureUrn(STRUCTURE_URN)).thenReturn(true);
    when(modelRegistryGateway.logicalUrn(STRUCTURE_URN)).thenReturn(STRUCTURE_URN);
    when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(STRUCTURE_URN + ":"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                importService.importDataSet(bundle(List.of(), List.of(sourceInput(STRUCTURE_URN)))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("neither part of this bundle nor");

    verify(dataSourceService, never()).create(any());
    verify(dataSetService, never()).create(any());
  }

  @Test
  void importDataSet_whenSourceReferenceIsNoDataStructureUrn_rejects() {
    when(modelRegistryGateway.isDataStructureUrn("not-a-urn")).thenReturn(false);

    assertThatThrownBy(
            () -> importService.importDataSet(bundle(List.of(), List.of(sourceInput("not-a-urn")))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("dataStructureUrn");

    verify(dataSourceService, never()).create(any());
    verify(dataSetService, never()).create(any());
  }

  @Test
  void importDataSet_withUnsupportedParts_rejectsWithClearMessage() {
    DataSetImportInputDTO input = bundle(List.of(), List.of());
    input.setPipelines(List.of(Map.of("name", "pipeline")));

    assertThatThrownBy(() -> importService.importDataSet(input))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("not yet supported");

    verify(dataStructureImportService, never()).importOrReuse(any());
    verify(dataSetService, never()).create(any());
  }
}
