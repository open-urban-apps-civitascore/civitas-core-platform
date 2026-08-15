package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.BundleInstallation;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.input.DataSetImportInputDTO;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSourceImportInputDTO;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataStructureImportInputDTO;
import de.civitascore.portal.model.input.MappingImportInputDTO;
import de.civitascore.portal.model.output.DataSetImportOutputDTO;
import de.civitascore.portal.model.output.DataSetImportOutputDTO.ImportedArtifactDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.BundleInstallationRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.service.DataStructureImportService.ImportResolution;
import de.civitascore.portal.service.MappingImportService.MappingResolution;
import de.civitascore.portal.util.InvalidInputException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a self-contained dataset bundle in one call: data structures first (created, or reused
 * when the same URN identity is already installed with identical content), then data sources
 * resolving their structure reference by URN, then mappings resolving theirs, then the dataset
 * shell — all in one transaction, so a rejected artifact rolls back the whole install.
 *
 * <p>Contained structures are released to AVAILABLE right away (a catalogue artifact is finished
 * content, and sources can only link to AVAILABLE versions — see {@link
 * DataStructureImportService#ensureAvailable}); the dataset shell and its sources stay DRAFT, so
 * release remains a separate, permission-gated step and no saga is touched here ({@link
 * DataSetService} publishes infrastructure sagas only on dataset release).
 *
 * <p>Supported bundle parts: dataset shell + data structures + data sources + mappings. Pipelines
 * and data sinks are rejected with an explicit message until their increments land.
 */
@Service
@RequiredArgsConstructor
public class DataSetImportService {

  private final DataStructureImportService dataStructureImportService;
  private final MappingImportService mappingImportService;
  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final ModelRegistryGateway modelRegistryGateway;
  private final DataSourceService dataSourceService;
  private final DataSetService dataSetService;
  private final BundleInstallationRepository bundleInstallationRepository;

  /**
   * Imports the bundle. Contained structures and mappings resolve by URN identity (create / reuse /
   * conflict); sources and mappings may reference structures from the bundle or already installed
   * ones.
   *
   * @param input the self-contained bundle
   * @return a summary naming the created dataset and what happened to each contained artifact
   * @throws InvalidInputException for unsupported bundle parts, unresolvable structure references,
   *     or invalid contained artifacts (400)
   * @throws de.civitascore.portal.util.UniqueConstraintViolationException when a contained
   *     structure or mapping identity is installed with different content (409)
   */
  @Transactional
  public DataSetImportOutputDTO importDataSet(DataSetImportInputDTO input) {
    rejectUnsupportedParts(input);

    // 1 · Structures: create or reuse, keyed by logical URN for the sources to reference.
    // Response and provenance lines are both derived from the domain result — neither view
    // feeds the other, so a cosmetic response change can never alter the recorded history.
    Map<String, ImportResolution> structuresByLogicalUrn = new LinkedHashMap<>();
    List<InstalledArtifact> artifactLines = new ArrayList<>();
    List<ImportedArtifactDTO> structureResults = new ArrayList<>();
    for (DataStructureImportInputDTO structure : input.getDataStructures()) {
      ImportResolution resolution = dataStructureImportService.importOrReuse(structure);
      dataStructureImportService.ensureAvailable(resolution.version());
      String logicalUrn = modelRegistryGateway.logicalUrn(resolution.version().getModelUrn());
      structuresByLogicalUrn.put(logicalUrn, resolution);
      UUID shellId = resolution.version().getDataStructure().getId();
      InstalledArtifactAction action =
          resolution.reused() ? InstalledArtifactAction.REUSED : InstalledArtifactAction.CREATED;
      artifactLines.add(
          artifactLine(
              InstalledArtifactType.DATA_STRUCTURE,
              structure.getName(),
              shellId,
              logicalUrn,
              action));
      structureResults.add(
          ImportedArtifactDTO.builder()
              .name(structure.getName())
              .id(shellId)
              .urn(logicalUrn)
              .action(action)
              .build());
    }

    // 2 · Sources: resolve the structure reference (bundle first, then installed), then create.
    List<ImportedArtifactDTO> sourceResults = new ArrayList<>();
    for (DataSourceImportInputDTO source : input.getDataSources()) {
      DataStructureVersion version = resolveStructureReference(source, structuresByLogicalUrn);
      DataSource created = dataSourceService.create(toDataSourceInput(source, version));
      // A data source has no registry identity of its own. Recording the referenced structure's
      // URN here would duplicate that URN in the provenance — and credit it to the wrong bundle
      // when the structure was merely reused — so the urn stays null for sources.
      artifactLines.add(
          artifactLine(
              InstalledArtifactType.DATA_SOURCE,
              created.getName(),
              created.getId(),
              null,
              InstalledArtifactAction.CREATED));
      sourceResults.add(
          ImportedArtifactDTO.builder()
              .name(created.getName())
              .id(created.getId())
              .urn(null)
              .action(InstalledArtifactAction.CREATED)
              .build());
    }

    // 3 · Mappings: registry-only artifacts, stored under the URN the bundle authored. After the
    // structures, whose identities their source/target must resolve against, and after the sources
    // so provenance and response read in chain order (source → mapping).
    List<ImportedArtifactDTO> mappingResults = new ArrayList<>();
    List<String> mappingUrns = new ArrayList<>();
    for (MappingImportInputDTO mapping : input.getMappings()) {
      requireResolvableStructureReferences(mapping, structuresByLogicalUrn);
      MappingResolution resolution = mappingImportService.importOrReuse(mapping);
      InstalledArtifactAction action =
          resolution.reused() ? InstalledArtifactAction.REUSED : InstalledArtifactAction.CREATED;
      mappingUrns.add(resolution.logicalUrn());
      // Mirror image of a data source: a mapping has registry identity but no shell row, so the
      // line carries the urn and leaves shellId null.
      artifactLines.add(
          artifactLine(
              InstalledArtifactType.MAPPING,
              mapping.getName(),
              null,
              resolution.logicalUrn(),
              action));
      mappingResults.add(
          ImportedArtifactDTO.builder()
              .name(mapping.getName())
              .id(null)
              .urn(resolution.logicalUrn())
              .action(action)
              .build());
    }

    // 4 · The dataset shell, last — the host artifacts do not reference it.
    DataSetInputDTO dataSetInput = new DataSetInputDTO();
    dataSetInput.setName(input.getName());
    dataSetInput.setDescription(input.getDescription());
    dataSetInput.setDatapoolId(input.getDatapoolId());
    dataSetInput.setAssignments(input.getAssignments());
    DataSet dataSet = dataSetService.create(dataSetInput);

    // 5 · Manifest membership for the registry-only artifacts. Without it a bundled mapping belongs
    // to no dataset and shows up under the "orphans by type" query — the use case would install its
    // mapping and then disown it. Cannot happen inside the loop above: the manifest exists only
    // once
    // DataSetService has persisted the shell.
    String manifestUrn = dataSet.getManifestLogicalUrn();
    if (manifestUrn != null) {
      mappingUrns.forEach(urn -> modelRegistryGateway.linkToDataSet(manifestUrn, urn));
    }

    // 6 · Provenance, in the same transaction: the record exists exactly iff the install
    // committed. Without it the created/reused knowledge dies with this HTTP response, and
    // "installed by a bundle" versus "created by hand" is unanswerable later.
    BundleInstallation installation = recordInstallation(input, dataSet, artifactLines);

    DataSetImportOutputDTO output = new DataSetImportOutputDTO();
    output.setDataSetId(dataSet.getId());
    output.setDataSetName(dataSet.getName());
    output.setInstallationId(installation.getId());
    output.setDataStructures(structureResults);
    output.setDataSources(sourceResults);
    output.setMappings(mappingResults);
    return output;
  }

  private BundleInstallation recordInstallation(
      DataSetImportInputDTO input, DataSet dataSet, List<InstalledArtifact> artifactLines) {
    BundleInstallation installation = new BundleInstallation();
    installation.setBundleUrn(input.getBundleUrn());
    installation.setBundleVersion(input.getBundleVersion());
    installation.setDataSetId(dataSet.getId());
    installation.setDataSetName(dataSet.getName());
    artifactLines.forEach(installation::addArtifact);
    return bundleInstallationRepository.save(installation);
  }

  private static InstalledArtifact artifactLine(
      InstalledArtifactType type,
      String name,
      UUID shellId,
      String urn,
      InstalledArtifactAction action) {
    InstalledArtifact artifact = new InstalledArtifact();
    artifact.setArtifactType(type);
    artifact.setName(name);
    artifact.setShellId(shellId);
    artifact.setUrn(urn);
    artifact.setAction(action);
    return artifact;
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
      // Already ensured AVAILABLE by the structure step.
      return bundled.version();
    }
    return dataStructureVersionRepository
        .findFirstByModelUrnStartingWith(logicalUrn + ":")
        // Installed-but-never-released structures (e.g. from the single-structure import, which
        // deliberately leaves everything DRAFT) must be released before a source can link.
        .map(
            installed -> {
              dataStructureImportService.ensureAvailable(installed);
              return installed;
            })
        .orElseThrow(
            () ->
                new InvalidInputException(
                    "DataSource",
                    source.getName(),
                    "references data structure '%s', which is neither part of this bundle nor"
                            .formatted(logicalUrn)
                        + " installed"));
  }

  /**
   * Guards the data structures a mapping names in {@code source}/{@code target}: each must be a
   * {@code :datastructure:} CORE URN that this bundle ships or that is already installed.
   *
   * <p>This has to happen in the host. Model Forge does not existence-check these references: its
   * {@code x-core-ref} validation reads annotations off the written document, and those annotations
   * live in {@code mapping.schema.json} (the meta-schema), not in a mapping instance. An unchecked
   * typo would install "successfully" as a dangling graph edge and only surface when a pipeline
   * tries to deploy it. Both keys are optional in the schema, so only present ones are checked — a
   * mapping without them is legal.
   */
  private void requireResolvableStructureReferences(
      MappingImportInputDTO mapping, Map<String, ImportResolution> structuresByLogicalUrn) {
    Map<String, Object> document = mapping.getDocument();
    if (document == null) {
      return;
    }
    for (String key : List.of("source", "target")) {
      if (!(document.get(key) instanceof String reference) || reference.isBlank()) {
        continue;
      }
      if (!modelRegistryGateway.isDataStructureUrn(reference)) {
        throw new InvalidInputException(
            "Mapping",
            mapping.getName(),
            "%s must be a CORE URN of artifact type 'datastructure', got: %s"
                .formatted(key, reference));
      }
      String logicalUrn = modelRegistryGateway.logicalUrn(reference);
      if (structuresByLogicalUrn.containsKey(logicalUrn)) {
        continue;
      }
      if (dataStructureVersionRepository
          .findFirstByModelUrnStartingWith(logicalUrn + ":")
          .isEmpty()) {
        throw new InvalidInputException(
            "Mapping",
            mapping.getName(),
            "%s references data structure '%s', which is neither part of this bundle nor installed"
                .formatted(key, logicalUrn));
      }
    }
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
        (input.getPipelines() != null && !input.getPipelines().isEmpty())
            || (input.getDataSinks() != null && !input.getDataSinks().isEmpty());
    if (hasUnsupported) {
      throw new InvalidInputException(
          "DataSet",
          input.getName(),
          "This import currently supports the dataset shell, dataStructures, dataSources and"
              + " mappings. Pipelines and dataSinks are not yet supported by the import endpoint.");
    }
  }
}
