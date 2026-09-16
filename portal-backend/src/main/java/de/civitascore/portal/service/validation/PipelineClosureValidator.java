package de.civitascore.portal.service.validation;

import de.civitascore.portal.configuration.PipelineClosureValidationProperties;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import de.civitascore.portal.util.PipelineClosureValidationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Refuses to let a Data Set be staged or released while an artifact its flows depend on cannot
 * carry a release. Provisioning configures NiFi, FROST, PostGIS, GeoServer and APISIX, so an
 * artifact that proves unusable afterwards has to be undone through saga compensation.
 *
 * <p>What participates is decided by the flow, not by Data Set membership: the walk starts at each
 * Pipeline's model and follows the references the registry recorded. Pipeline content is never
 * parsed, and a Data Set member no flow reaches does not block it.
 *
 * <p>Every artifact reached must resolve. One the platform holds a Data Structure Version for must
 * also be readable by the caller and released — that record, not the URN's kind, is what gives it a
 * lifecycle and a scope. One without a record is held to resolvability alone: elements are
 * deliberately reusable, so inheriting a lifecycle would block a shared element on any unrelated
 * draft.
 *
 * <p>Findings are collected across every flow and reported together.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PipelineClosureValidator {

  /**
   * How many pinned URNs one record lookup may carry. A flow can reach more artifacts than a single
   * statement has bind slots, so the lookup is chunked the way the registry chunks its own
   * existence probe.
   */
  private static final int RECORD_LOOKUP_BATCH_SIZE = 500;

  private final ModelRegistryGateway modelRegistryGateway;
  private final DataStructureVersionRepository dataStructureVersionRepository;
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
    // One verdict per structure for the whole call: several versions, and several flows, routinely
    // reach the same structure, and each decision otherwise re-reads the caller's assignments.
    Map<UUID, Boolean> readability = new HashMap<>();
    for (Pipeline pipeline : pipelines) {
      if (pipeline.getModelUrn() == null || pipeline.getModelUrn().isBlank()) {
        continue;
      }
      if (flowBlocks(pipeline, readability)) {
        offending.add(pipeline.getId());
      }
    }
    if (!offending.isEmpty()) {
      throw new PipelineClosureValidationException(List.copyOf(offending));
    }
  }

  /**
   * Whether this flow blocks a release. Every reason is logged rather than returned: the reply
   * names the pipeline only, so the log is where an operator learns which artifact and why.
   */
  private boolean flowBlocks(Pipeline pipeline, Map<UUID, Boolean> readability) {
    ModelRegistryGateway.ArtifactClosure closure =
        modelRegistryGateway.closure(pipeline.getModelUrn(), properties.maxDepth());

    boolean blocks = false;
    if (closure.truncated()) {
      // Passing here would report "nothing found" for a flow nobody walked to its end.
      log.warn(
          "Closure validation: the flow of pipeline {} reaches beyond the configured depth of {}",
          pipeline.getId(),
          properties.maxDepth());
      blocks = true;
    }
    for (String urn : closure.unresolved()) {
      log.info(
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
    Map<String, List<DataStructureVersion>> governed =
        recordsPinnedBy(resolved).stream()
            .collect(Collectors.groupingBy(DataStructureVersion::getModelUrn));
    for (String urn : resolved) {
      List<DataStructureVersion> records = governed.get(urn);
      if (records != null && governedBlocks(pipeline.getId(), urn, records, readability)) {
        blocks = true;
      }
    }
    return blocks;
  }

  /** Every version record pinning one of these URNs, asked in batches a statement can carry. */
  private List<DataStructureVersion> recordsPinnedBy(List<String> urns) {
    if (urns.size() <= RECORD_LOOKUP_BATCH_SIZE) {
      return dataStructureVersionRepository.findAllByModelUrnIn(urns);
    }
    List<DataStructureVersion> records = new ArrayList<>();
    for (int from = 0; from < urns.size(); from += RECORD_LOOKUP_BATCH_SIZE) {
      records.addAll(
          dataStructureVersionRepository.findAllByModelUrnIn(
              urns.subList(from, Math.min(from + RECORD_LOOKUP_BATCH_SIZE, urns.size()))));
    }
    return records;
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
