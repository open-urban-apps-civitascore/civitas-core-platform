package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSetImportInputDTO;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSinkImportInputDTO;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.input.DataSourceImportInputDTO;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataStructureImportInputDTO;
import de.civitascore.portal.model.input.MappingImportInputDTO;
import de.civitascore.portal.model.input.PipelineImportInputDTO;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.DataSetImportOutputDTO;
import de.civitascore.portal.model.output.DataSetImportOutputDTO.ImportedArtifactDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.service.DataStructureImportService.ImportResolution;
import de.civitascore.portal.service.MappingImportService.MappingResolution;
import de.civitascore.portal.util.InvalidInputException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a self-contained dataset bundle in one call: data structures first (created, or reused
 * when the same URN identity is already installed with identical content), then data sources
 * resolving their structure reference by URN, then mappings resolving theirs, then the dataset
 * shell, then the sinks and pipelines that hang off it — all in one transaction, so a rejected
 * artifact rolls back the whole install.
 *
 * <p>Contained structures are released to AVAILABLE right away (a catalogue artifact is finished
 * content, and sources can only link to AVAILABLE versions — see {@link
 * DataStructureImportService#ensureAvailable}). Sources a bundle pipeline wires in are released for
 * the same reason: {@link PipelineService} only links AVAILABLE sources. The dataset shell itself
 * stays DRAFT, so release remains a separate, permission-gated step and no saga is touched here
 * ({@link DataSetService} publishes infrastructure sagas only on dataset release).
 *
 * <p>Sinks and pipelines carry no portable identity — their URNs are minted by the receiving
 * instance — so unlike structures and mappings they are always CREATED, never reused, and the
 * bundle references them by bundle-local name rather than by URN. ("Bundle" here always means the
 * artifact set of one import call — unrelated to Model Forge's read-side "bundled view".)
 */
@Service
@RequiredArgsConstructor
public class DataSetImportService {

  private final DataStructureImportService dataStructureImportService;
  private final MappingImportService mappingImportService;
  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final ModelRegistryGateway modelRegistryGateway;
  private final DataSourceService dataSourceService;
  private final DataSinkService dataSinkService;
  private final PipelineService pipelineService;
  private final DataSetService dataSetService;
  private final InstallationRecorder installationRecorder;

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
          InstallationRecorder.line(
              InstalledArtifactType.DATA_STRUCTURE,
              structure.getName(),
              shellId,
              logicalUrn,
              resolution.version().getModelUrn(),
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
    // Collected by bundle-local name for the pipelines to reference; a duplicated name is only an
    // error if a pipeline actually references it — resolveByName rejects the ambiguity then.
    List<ImportedArtifactDTO> sourceResults = new ArrayList<>();
    Map<String, DataSource> sourcesByName = new LinkedHashMap<>();
    Set<String> ambiguousNames = new HashSet<>();
    for (DataSourceImportInputDTO source : input.getDataSources()) {
      DataStructureVersion version =
          resolveStructureReference(
              source.getDataStructureUrn(), "DataSource", source.getName(), structuresByLogicalUrn);
      DataSource created = dataSourceService.create(toDataSourceInput(source, version));
      if (sourcesByName.putIfAbsent(created.getName(), created) != null) {
        ambiguousNames.add(created.getName());
      }
      // A data source has no registry identity of its own. Recording the referenced structure's
      // URN here would duplicate that URN in the provenance — and credit it to the wrong bundle
      // when the structure was merely reused — so the urn stays null for sources.
      artifactLines.add(
          InstallationRecorder.line(
              InstalledArtifactType.DATA_SOURCE,
              created.getName(),
              created.getId(),
              null,
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
    Map<String, String> mappingUrnsByName = new LinkedHashMap<>();
    for (MappingImportInputDTO mapping : input.getMappings()) {
      requireResolvableStructureReferences(mapping, structuresByLogicalUrn);
      MappingResolution resolution = mappingImportService.importOrReuse(mapping);
      InstalledArtifactAction action =
          resolution.reused() ? InstalledArtifactAction.REUSED : InstalledArtifactAction.CREATED;
      mappingUrns.add(resolution.logicalUrn());
      // Pipelines pin the VERSIONED urn: a logical reference silently means "current version",
      // and the platform convention documents only pinned or :latest reference forms.
      if (mappingUrnsByName.putIfAbsent(mapping.getName(), resolution.versionedUrn()) != null) {
        ambiguousNames.add(mapping.getName());
      }
      // Mirror image of a data source: a mapping has registry identity but no shell row, so the
      // line carries the urn and leaves shellId null.
      artifactLines.add(
          InstallationRecorder.line(
              InstalledArtifactType.MAPPING,
              mapping.getName(),
              null,
              resolution.logicalUrn(),
              resolution.versionedUrn(),
              action));
      mappingResults.add(
          ImportedArtifactDTO.builder()
              .name(mapping.getName())
              .id(null)
              .urn(resolution.logicalUrn())
              .action(action)
              .build());
    }

    // 4 · The dataset shell — before sinks and pipelines, which are created on it. It gets its
    // provenance line here, in touch order; it has both a shell row and a registry identity (its
    // manifest), so unlike a source or a mapping the line carries both.
    DataSetInputDTO dataSetInput = new DataSetInputDTO();
    dataSetInput.setName(input.getName());
    dataSetInput.setDescription(input.getDescription());
    dataSetInput.setDatapoolId(input.getDatapoolId());
    dataSetInput.setAssignments(input.getAssignments());
    DataSet dataSet = dataSetService.create(dataSetInput);
    String manifestUrn = dataSet.getManifestLogicalUrn();
    artifactLines.add(
        InstallationRecorder.line(
            InstalledArtifactType.DATA_SET,
            dataSet.getName(),
            dataSet.getId(),
            manifestUrn,
            dataSet.getManifestUrn(),
            InstalledArtifactAction.CREATED));

    // 5 · Sinks, before the pipelines that link them. The bundle authors the target structure in
    // configuration.element as a CORE URN; the sink contract wants the resolved version's model
    // URN, so the reference is rewritten before DataSinkService validates and stores it. Sink
    // names are the handles pipelines resolve against, so they must be unique outright.
    List<ImportedArtifactDTO> sinkResults = new ArrayList<>();
    Map<String, DataSink> sinksByName = new LinkedHashMap<>();
    for (DataSinkImportInputDTO sink : input.getDataSinks()) {
      if (sinksByName.containsKey(sink.getName())) {
        throw new InvalidInputException(
            "DataSink", sink.getName(), "duplicate data sink name in bundle");
      }
      DataSink created =
          dataSinkService.create(toDataSinkInput(sink, dataSet, structuresByLogicalUrn));
      sinksByName.put(sink.getName(), created);
      artifactLines.add(
          InstallationRecorder.line(
              InstalledArtifactType.DATA_SINK,
              sink.getName(),
              created.getId(),
              created.getConfigurationLogicalUrn(),
              created.getConfigurationUrn(),
              InstalledArtifactAction.CREATED));
      sinkResults.add(
          ImportedArtifactDTO.builder()
              .name(sink.getName())
              .id(created.getId())
              .urn(created.getConfigurationLogicalUrn())
              .action(InstalledArtifactAction.CREATED)
              .build());
    }

    // 6 · Pipelines. The graph is the single source of truth: name references are rewritten to
    // the created artifacts' minted URNs, and the source/sink links are derived from exactly
    // those resolutions — no separate id lists that could drift from the graph.
    List<ImportedArtifactDTO> pipelineResults = new ArrayList<>();
    for (PipelineImportInputDTO pipeline : input.getPipelines()) {
      PipelineWiring wiring =
          resolvePipelineReferences(
              pipeline, sourcesByName, sinksByName, mappingUrnsByName, ambiguousNames);
      // A pipeline can only link AVAILABLE sources; a bundle source it wires in is finished
      // catalogue content, so release it — same rationale (and same upstream design note) as
      // DataStructureImportService.ensureAvailable for structures.
      wiring.linkedSources().stream()
          .filter(source -> source.getDataSourceStatus() == DataSourceStatus.DRAFT)
          .forEach(source -> dataSourceService.release(source.getId()));
      Pipeline created = pipelineService.create(toPipelineInput(pipeline, dataSet, wiring));
      artifactLines.add(
          InstallationRecorder.line(
              InstalledArtifactType.PIPELINE,
              pipeline.getName(),
              created.getId(),
              created.getModelLogicalUrn(),
              created.getModelUrn(),
              InstalledArtifactAction.CREATED));
      pipelineResults.add(
          ImportedArtifactDTO.builder()
              .name(pipeline.getName())
              .id(created.getId())
              .urn(created.getModelLogicalUrn())
              .action(InstalledArtifactAction.CREATED)
              .build());
    }

    // 7 · Manifest membership for EVERY member the bundle touched. The CORE-IR manifest is the
    // bracketing document — a member the manifest does not reference shows up under the
    // "orphans by type" query although it belongs to this use case. A pipeline links its own
    // reference closure when PipelineService stores it, but a pipeline-less bundle has no
    // closure — so link explicitly; Model Forge skips members already listed.
    if (manifestUrn != null) {
      structuresByLogicalUrn
          .keySet()
          .forEach(urn -> modelRegistryGateway.linkToDataSet(manifestUrn, urn));
      sourcesByName.values().stream()
          .map(DataSource::getConfigurationLogicalUrn)
          .filter(Objects::nonNull)
          .forEach(urn -> modelRegistryGateway.linkToDataSet(manifestUrn, urn));
      mappingUrns.forEach(urn -> modelRegistryGateway.linkToDataSet(manifestUrn, urn));
      sinksByName.values().stream()
          .map(DataSink::getConfigurationLogicalUrn)
          .filter(Objects::nonNull)
          .forEach(urn -> modelRegistryGateway.linkToDataSet(manifestUrn, urn));
    }

    // 8 · Provenance, in the same transaction: the record exists exactly iff the install
    // committed. Without it the created/reused knowledge dies with this HTTP response, and
    // "installed by a bundle" versus "created by hand" is unanswerable later.
    Installation installation =
        installationRecorder.record(
            input.getCatalogEntryId(),
            input.getCatalogEntryVersion(),
            dataSet.getId(),
            dataSet.getName(),
            artifactLines);

    DataSetImportOutputDTO output = new DataSetImportOutputDTO();
    output.setDataSetId(dataSet.getId());
    output.setDataSetName(dataSet.getName());
    output.setInstallationId(installation.getId());
    output.setDataStructures(structureResults);
    output.setDataSources(sourceResults);
    output.setMappings(mappingResults);
    output.setDataSinks(sinkResults);
    output.setPipelines(pipelineResults);
    return output;
  }

  /**
   * Resolves a data structure reference for any bundle artifact that carries one — a source's
   * {@code dataStructureUrn} or a sink's {@code configuration.element}. Bundle structures win over
   * installed ones; an installed DRAFT structure is released on the way (a structure a bundle wires
   * into its flow is finished catalogue content).
   */
  private DataStructureVersion resolveStructureReference(
      String reference,
      String artifactKind,
      String artifactName,
      Map<String, ImportResolution> structuresByLogicalUrn) {
    if (!modelRegistryGateway.isDataStructureUrn(reference)) {
      throw new InvalidInputException(
          artifactKind,
          artifactName,
          "structure reference must be a CORE URN of artifact type 'datastructure', got: "
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
                    artifactKind,
                    artifactName,
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

  private DataSinkInputDTO toDataSinkInput(
      DataSinkImportInputDTO sink,
      DataSet dataSet,
      Map<String, ImportResolution> structuresByLogicalUrn) {
    DataSinkInputDTO dto = new DataSinkInputDTO();
    dto.setDataSinkType(sink.getDataSinkType());
    dto.setDataSetId(dataSet.getId());
    Map<String, Object> configuration = sink.getConfiguration();
    if (configuration != null) {
      // Copy before rewriting — the input DTO stays as authored.
      Map<String, Object> rewritten = new LinkedHashMap<>(configuration);
      if (rewritten.get("element") instanceof String element && !element.isBlank()) {
        DataStructureVersion version =
            resolveStructureReference(element, "DataSink", sink.getName(), structuresByLogicalUrn);
        // The versioned model URN — what DataSinkService validates and Model Forge records as
        // the datasink-element edge. The bundle authors the logical URN because it cannot know
        // which version the receiving instance resolves.
        rewritten.put("element", version.getModelUrn());
      }
      dto.setConfiguration(rewritten);
    }
    return dto;
  }

  /**
   * The wiring one bundle pipeline resolves to: its graph with every name reference rewritten to a
   * minted CORE URN, and the source/sink links derived from those resolutions. Sources are kept as
   * entities because the caller must release DRAFT ones before {@link PipelineService} will link
   * them.
   */
  private record PipelineWiring(
      Map<String, Object> model, List<DataSource> linkedSources, Set<UUID> sinkIds) {}

  /**
   * Walks the pipeline graph and resolves its references. A {@code sourceRef}/{@code
   * sinkRef}/{@code mappingRef} value starting with {@code urn:} passes through verbatim (and
   * derives no link — the artifact is expected to exist on the instance); any other value must name
   * a bundle member of the matching type. The input model is never mutated.
   */
  private PipelineWiring resolvePipelineReferences(
      PipelineImportInputDTO pipeline,
      Map<String, DataSource> sourcesByName,
      Map<String, DataSink> sinksByName,
      Map<String, String> mappingUrnsByName,
      Set<String> ambiguousNames) {
    Map<String, Object> model = pipeline.getModel();
    List<DataSource> linkedSources = new ArrayList<>();
    Set<UUID> sinkIds = new LinkedHashSet<>();
    if (model == null || !(model.get("nodes") instanceof List<?> nodes)) {
      return new PipelineWiring(model, linkedSources, sinkIds);
    }

    List<Object> rewrittenNodes = new ArrayList<>(nodes.size());
    for (Object nodeRaw : nodes) {
      if (!(nodeRaw instanceof Map<?, ?> nodeMap)) {
        rewrittenNodes.add(nodeRaw);
        continue;
      }
      Map<String, Object> node = new LinkedHashMap<>();
      nodeMap.forEach((key, value) -> node.put(String.valueOf(key), value));

      rewriteReference(
          pipeline,
          node,
          "sourceRef",
          ambiguousNames,
          name -> {
            DataSource source = sourcesByName.get(name);
            if (source == null) {
              return null;
            }
            // Found, but not referencable: the graph needs the minted configuration URN, and a
            // source without connector configuration never got one. Conflating this with "name
            // unknown" sends the author hunting for a typo that does not exist.
            if (source.getConfigurationUrn() == null) {
              throw new InvalidInputException(
                  "Pipeline",
                  pipeline.getName(),
                  ("sourceRef '%s' resolves to a bundle data source without connector"
                          + " configuration — a pipeline cannot reference it")
                      .formatted(name));
            }
            linkedSources.add(source);
            return source.getConfigurationUrn();
          });
      rewriteReference(
          pipeline,
          node,
          "sinkRef",
          ambiguousNames,
          name -> {
            DataSink sink = sinksByName.get(name);
            if (sink == null) {
              return null;
            }
            // Same distinction as for sources: a FROST sink created without configuration has no
            // minted URN a graph could point at.
            if (sink.getConfigurationUrn() == null) {
              throw new InvalidInputException(
                  "Pipeline",
                  pipeline.getName(),
                  ("sinkRef '%s' resolves to a bundle data sink without configuration — a"
                          + " pipeline cannot reference it")
                      .formatted(name));
            }
            sinkIds.add(sink.getId());
            return sink.getConfigurationUrn();
          });
      rewriteReference(pipeline, node, "mappingRef", ambiguousNames, mappingUrnsByName::get);

      rewrittenNodes.add(node);
    }

    Map<String, Object> rewrittenModel = new LinkedHashMap<>(model);
    rewrittenModel.put("nodes", rewrittenNodes);
    return new PipelineWiring(rewrittenModel, linkedSources, sinkIds);
  }

  private void rewriteReference(
      PipelineImportInputDTO pipeline,
      Map<String, Object> node,
      String refKey,
      Set<String> ambiguousNames,
      Function<String, String> resolveName) {
    if (!(node.get(refKey) instanceof String reference)
        || reference.isBlank()
        || reference.startsWith("urn:")) {
      return;
    }
    if (ambiguousNames.contains(reference)) {
      throw new InvalidInputException(
          "Pipeline",
          pipeline.getName(),
          "%s '%s' is ambiguous — more than one bundle artifact carries that name"
              .formatted(refKey, reference));
    }
    String resolved = resolveName.apply(reference);
    if (resolved == null) {
      throw new InvalidInputException(
          "Pipeline",
          pipeline.getName(),
          "%s references '%s', which is not part of this bundle".formatted(refKey, reference));
    }
    node.put(refKey, resolved);
  }

  private PipelineInputDTO toPipelineInput(
      PipelineImportInputDTO pipeline, DataSet dataSet, PipelineWiring wiring) {
    PipelineInputDTO dto = new PipelineInputDTO();
    dto.setName(pipeline.getName());
    dto.setDescription(pipeline.getDescription());
    dto.setDataSetId(dataSet.getId());
    dto.setModel(wiring.model());
    dto.setStyles(pipeline.getStyles());
    Set<UUID> sourceIds =
        wiring.linkedSources().stream()
            .map(DataSource::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    dto.setDataSourceIds(sourceIds.isEmpty() ? null : sourceIds);
    dto.setDataSinkIds(wiring.sinkIds().isEmpty() ? null : wiring.sinkIds());
    return dto;
  }
}
