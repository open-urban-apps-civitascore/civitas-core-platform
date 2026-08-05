package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataSetImportInputDTO;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSourceImportInputDTO;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataStructureImportInputDTO;
import de.civitascore.portal.model.output.DataSetImportOutputDTO;
import de.civitascore.portal.model.output.DataSetImportOutputDTO.ImportedArtifactDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.service.DataStructureImportService.ImportResolution;
import de.civitascore.portal.util.InvalidInputException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a self-contained dataset bundle in one call: data structures first (created, or reused
 * when the same URN identity is already installed with identical content), then data sources
 * resolving their structure reference by URN, then the dataset shell — all in one transaction, so a
 * rejected artifact rolls back the whole install.
 *
 * <p>Like the single-structure import, everything is created in DRAFT and release stays a separate,
 * permission-gated step; no saga is touched here ({@link DataSetService} publishes infrastructure
 * sagas only on release).
 *
 * <p>Increment 1: dataset shell + data structures + data sources. Mappings, pipelines and data
 * sinks are rejected with an explicit message until their increments land.
 */
@Service
@RequiredArgsConstructor
public class DataSetImportService {

  private final DataStructureImportService dataStructureImportService;
  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final ModelRegistryGateway modelRegistryGateway;
  private final DataSourceService dataSourceService;
  private final DataSetService dataSetService;

  /**
   * Imports the bundle. Contained structures resolve by URN identity (create / reuse / conflict);
   * sources may reference structures from the bundle or already installed ones.
   *
   * @param input the self-contained bundle
   * @return a summary naming the created dataset and what happened to each contained artifact
   * @throws InvalidInputException for unsupported bundle parts, unresolvable structure references,
   *     or invalid contained artifacts (400)
   * @throws de.civitascore.portal.util.UniqueConstraintViolationException when a contained
   *     structure's identity is installed with different content (409)
   */
  @Transactional
  public DataSetImportOutputDTO importDataSet(DataSetImportInputDTO input) {
    rejectUnsupportedParts(input);

    // 1 · Structures: create or reuse, keyed by logical URN for the sources to reference.
    Map<String, ImportResolution> structuresByLogicalUrn = new LinkedHashMap<>();
    List<ImportedArtifactDTO> structureResults = new ArrayList<>();
    for (DataStructureImportInputDTO structure : input.getDataStructures()) {
      ImportResolution resolution = dataStructureImportService.importOrReuse(structure);
      String logicalUrn = modelRegistryGateway.logicalUrn(resolution.version().getModelUrn());
      structuresByLogicalUrn.put(logicalUrn, resolution);
      structureResults.add(
          ImportedArtifactDTO.builder()
              .name(structure.getName())
              .id(resolution.version().getDataStructure().getId())
              .urn(logicalUrn)
              .action(resolution.reused() ? "REUSED" : "CREATED")
              .build());
    }

    // 2 · Sources: resolve the structure reference (bundle first, then installed), then create.
    List<ImportedArtifactDTO> sourceResults = new ArrayList<>();
    for (DataSourceImportInputDTO source : input.getDataSources()) {
      DataStructureVersion version = resolveStructureReference(source, structuresByLogicalUrn);
      DataSource created = dataSourceService.create(toDataSourceInput(source, version));
      sourceResults.add(
          ImportedArtifactDTO.builder()
              .name(created.getName())
              .id(created.getId())
              .urn(modelRegistryGateway.logicalUrn(version.getModelUrn()))
              .action("CREATED")
              .build());
    }

    // 3 · The dataset shell, last — nothing may reference it yet in increment 1.
    DataSetInputDTO dataSetInput = new DataSetInputDTO();
    dataSetInput.setName(input.getName());
    dataSetInput.setDescription(input.getDescription());
    dataSetInput.setDatapoolId(input.getDatapoolId());
    dataSetInput.setAssignments(input.getAssignments());
    DataSet dataSet = dataSetService.create(dataSetInput);

    return DataSetImportOutputDTO.builder()
        .dataSetId(dataSet.getId())
        .dataSetName(dataSet.getName())
        .dataStructures(structureResults)
        .dataSources(sourceResults)
        .build();
  }

  private DataStructureVersion resolveStructureReference(
      DataSourceImportInputDTO source, Map<String, ImportResolution> structuresByLogicalUrn) {
    String reference = source.getDataStructureUrn();
    if (!modelRegistryGateway.isDataStructureUrn(reference)) {
      throw new InvalidInputException(
          "DataSource",
          source.getName(),
          "dataStructureUrn must be a CORE URN of artifact type 'datastructure', got: "
              + reference);
    }
    String logicalUrn = modelRegistryGateway.logicalUrn(reference);

    ImportResolution bundled = structuresByLogicalUrn.get(logicalUrn);
    if (bundled != null) {
      return bundled.version();
    }
    return dataStructureVersionRepository
        .findFirstByModelUrnStartingWith(logicalUrn + ":")
        .orElseThrow(
            () ->
                new InvalidInputException(
                    "DataSource",
                    source.getName(),
                    "references data structure '%s', which is neither part of this bundle nor"
                            .formatted(logicalUrn)
                        + " installed"));
  }

  private DataSourceInputDTO toDataSourceInput(
      DataSourceImportInputDTO source, DataStructureVersion version) {
    DataSourceInputDTO dto = new DataSourceInputDTO();
    dto.setName(source.getName());
    dto.setDescription(source.getDescription());
    dto.setConnectorType(source.getConnectorType());
    dto.setConfiguration(source.getConfiguration());
    dto.setDataStructureVersionId(version.getId());
    dto.setAssignments(source.getAssignments());
    return dto;
  }

  private void rejectUnsupportedParts(DataSetImportInputDTO input) {
    boolean hasUnsupported =
        (input.getMappings() != null && !input.getMappings().isEmpty())
            || (input.getPipelines() != null && !input.getPipelines().isEmpty())
            || (input.getDataSinks() != null && !input.getDataSinks().isEmpty());
    if (hasUnsupported) {
      throw new InvalidInputException(
          "DataSet",
          input.getName(),
          "This import currently supports the dataset shell, dataStructures and dataSources."
              + " Mappings, pipelines and dataSinks are not yet supported by the import"
              + " endpoint.");
    }
  }
}
