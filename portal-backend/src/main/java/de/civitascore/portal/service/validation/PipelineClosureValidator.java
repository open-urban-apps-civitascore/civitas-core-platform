package de.civitascore.portal.service.validation;

import de.civitascore.portal.configuration.PipelineClosureValidationProperties;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import de.civitascore.portal.service.GoverningVersionLookup;
import de.civitascore.portal.util.PipelineClosureValidationException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Refuses to let a Data Set be released while an artifact its flows depend on cannot carry a
 * release. Provisioning configures NiFi, FROST, PostGIS, GeoServer and APISIX, so an artifact that
 * proves unusable afterwards has to be undone through saga compensation.
 *
 * <p>A Pipeline with a stored model participates; one without contributes no flow. Its Data sources
 * are the ones referenced on the Pipeline itself, and the registry walk starts at its model and
 * follows the recorded references. Pipeline content is never parsed, and a Dataset member that no
 * flow reaches does not block it.
 *
 * <p>Every Data source of a participating Pipeline must be AVAILABLE. Every registry artifact must
 * resolve. An artifact with a Data structure version row must also be readable and released. The
 * row, not the URN's kind, gives it a lifecycle and a Scope. An artifact without a row is held to
 * resolvability alone. Elements are reusable, so inheriting a lifecycle would block a shared
 * element on an unrelated draft.
 *
 * <p>Findings are collected across every flow and reported together.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PipelineClosureValidator {

  private final ModelRegistryGateway modelRegistryGateway;
  private final GoverningVersionLookup governingVersions;
  private final ScopeAccessAuthorizer scopeAccessAuthorizer;
  private final PipelineClosureValidationProperties properties;

  /**
   * Validates the artifacts participating in every one of the Data Set's flows.
   *
   * @param pipelines the Data Set's pipelines; one with no stored model yet contributes no flow
   * @throws PipelineClosureValidationException naming every pipeline whose flow blocks, so one
   *     attempt reports them all
   */
  public void validate(Collection<Pipeline> pipelines) {
    if (pipelines == null || pipelines.isEmpty()) {
      return;
    }
    Set<UUID> offending = new LinkedHashSet<>();
    // Keep one verdict per structure for the whole call. Several versions and flows can reach the
    // same structure, and each decision otherwise reads the caller's Assignments again.
    Map<UUID, Boolean> structureReadability = new HashMap<>();
    for (Pipeline pipeline : pipelines) {
      if (pipeline.getModelUrn() == null || pipeline.getModelUrn().isBlank()) {
        continue;
      }
      if (flowBlocks(pipeline, structureReadability)) {
        offending.add(pipeline.getId());
      }
    }
    if (!offending.isEmpty()) {
      throw new PipelineClosureValidationException(List.copyOf(offending));
    }
  }

  /**
   * Whether this flow blocks a release. The reply names only the Pipeline; the log gives detail.
   */
  private boolean flowBlocks(Pipeline pipeline, Map<UUID, Boolean> structureReadability) {
    ModelRegistryGateway.ArtifactClosure closure =
        modelRegistryGateway.closure(pipeline.getModelUrn(), properties.maxDepth());

    boolean blocks = dataSourcesBlock(pipeline);
    if (closure.truncated()) {
      // Passing here would report "nothing found" for a flow nobody walked to its end.
      log.warn(
          "Closure validation: the flow of pipeline {} reaches beyond the configured depth of {}",
          pipeline.getId(),
          properties.maxDepth());
      blocks = true;
    }
    for (String urn : closure.unresolved()) {
      log.warn(
          "Closure validation: unresolved reference to {} reached by pipeline {}",
          Encode.forJava(urn),
          pipeline.getId());
      blocks = true;
    }
    List<String> resolved =
        closure.artifacts().stream().filter(urn -> !closure.unresolved().contains(urn)).toList();
    if (resolved.isEmpty()) {
      return blocks;
    }
    Map<String, List<DataStructureVersion>> governed = governingVersions.governingAll(resolved);
    for (String urn : resolved) {
      List<DataStructureVersion> records = governed.get(urn);
      if (records != null && governedBlocks(pipeline.getId(), urn, records, structureReadability)) {
        blocks = true;
      }
    }
    return blocks;
  }

  /**
   * Whether a Data source of this flow blocks a release. No permission on the Data source is
   * required: saving the Pipeline authorized the reference through the Use relationship, and the
   * reply discloses nothing about the Data source.
   */
  private boolean dataSourcesBlock(Pipeline pipeline) {
    boolean blocks = false;
    for (DataSource dataSource : pipeline.getDataSources()) {
      if (dataSource.getDataSourceStatus() != DataSourceStatus.AVAILABLE) {
        log.info(
            "Closure validation: data source {} reached by pipeline {} is still a draft",
            dataSource.getId(),
            pipeline.getId());
        blocks = true;
      }
    }
    return blocks;
  }

  /**
   * Holds one governed artifact to what a released flow needs of it. Readability is judged first:
   * reporting a draft state to a caller who may not read the artifact would confirm it exists.
   *
   * <p>Several records can pin the same artifact, so it carries a release as soon as one record the
   * caller may read says so.
   */
  private boolean governedBlocks(
      UUID pipelineId,
      String urn,
      List<DataStructureVersion> records,
      Map<UUID, Boolean> readability) {
    List<DataStructureVersion> readable =
        records.stream().filter(version -> isReadable(version, readability)).toList();
    if (readable.isEmpty()) {
      log.warn(
          "Closure validation: caller may not read any data structure holding {} reached by"
              + " pipeline {}",
          Encode.forJava(urn),
          pipelineId);
      return true;
    }
    if (readable.stream().noneMatch(PipelineClosureValidator::isReleased)) {
      log.info(
          "Closure validation: {} reached by pipeline {} is still a draft",
          Encode.forJava(urn),
          pipelineId);
      return true;
    }
    return false;
  }

  /**
   * Whether the caller may read the structure carrying this version. Decided here because OPA never
   * sees a structure two hops away, and the scope header it emits is typed to the route's own
   * scope.
   */
  private boolean isReadable(DataStructureVersion version, Map<UUID, Boolean> readability) {
    return readability.computeIfAbsent(
        version.getDataStructure().getId(), structureId -> decideReadable(version));
  }

  private boolean decideReadable(DataStructureVersion version) {
    try {
      scopeAccessAuthorizer.authorizeReferences(
          ScopeType.DATASTRUCTURE, Set.of(version.getDataStructure().getId()));
      return true;
    } catch (AccessDeniedException denied) {
      return false;
    }
  }

  /**
   * Whether a version counts as released. Both it and the structure carrying it must be: a released
   * version of a draft structure is not something a flow may publish.
   */
  private static boolean isReleased(DataStructureVersion version) {
    return version.getDataStructureVersionStatus() == DataStructureVersionStatus.AVAILABLE
        && version.getDataStructure().getDataStructureStatus() == DataStructureStatus.AVAILABLE;
  }
}
