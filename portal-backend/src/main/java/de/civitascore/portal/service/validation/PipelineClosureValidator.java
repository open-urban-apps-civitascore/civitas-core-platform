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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * carry a release.
 *
 * <p>Provisioning a released Data Set configures NiFi, FROST, PostGIS, GeoServer and APISIX. An
 * artifact that proves unusable once that has begun has to be undone through saga compensation, so
 * the question is asked before the transition rather than after.
 *
 * <p>What participates is decided by the flow, not by Data Set membership. The walk starts at each
 * Pipeline's own model and follows the references the registry recorded — a pipeline names a
 * mapping, a mapping names a source and a target structure, a structure names its member elements.
 * An artifact that belongs to the same Data Set but that no flow reaches is irrelevant here and
 * does not block it. The reference graph is the authority; pipeline content is never parsed.
 *
 * <p>Every artifact reached must resolve, which the registry answers for the whole closure at once.
 * An artifact the platform holds a Data Structure Version for is held to more, because that record
 * is what gives it a release lifecycle and an authorization scope: the caller must be allowed to
 * read it and it must itself be released. Being governed is decided by that record rather than by
 * the URN's kind — a version pins an {@code :element:} URN as readily as a {@code :datastructure:}
 * one, depending only on whether the model was authored with a diagram.
 *
 * <p>An artifact with no such record — a member element, a mapping, a sink configuration — is held
 * to resolvability alone. It carries no lifecycle of its own, and inheriting one would mean picking
 * a structure among several that may share it: elements are deliberately reusable, so a shared
 * element would otherwise be blocked by any unrelated draft that also uses it.
 *
 * <p>Findings are collected across every flow and reported together, so one attempt names
 * everything that needs repairing.
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
   * @throws PipelineClosureValidationException if any participating artifact cannot carry a
   *     release, carrying every finding across every flow
   */
  public void validate(Collection<Pipeline> pipelines) {
    if (pipelines == null || pipelines.isEmpty()) {
      return;
    }
    List<ClosureFinding> findings = new ArrayList<>();
    // One verdict per structure for the whole call: several versions, and several flows, routinely
    // reach the same structure, and each decision otherwise re-reads the caller's assignments.
    Map<UUID, Boolean> readability = new HashMap<>();
    for (Pipeline pipeline : pipelines) {
      if (pipeline.getModelUrn() == null || pipeline.getModelUrn().isBlank()) {
        continue;
      }
      findings.addAll(validateFlow(pipeline, readability));
    }
    if (!findings.isEmpty()) {
      // Findings that name no artifact are identical per pipeline and reason, so one flow with
      // many withheld artifacts would otherwise repeat the same entry once for each of them.
      throw new PipelineClosureValidationException(findings.stream().distinct().toList());
    }
  }

  private List<ClosureFinding> validateFlow(Pipeline pipeline, Map<UUID, Boolean> readability) {
    ModelRegistryGateway.ArtifactClosure closure =
        modelRegistryGateway.closure(pipeline.getModelUrn(), properties.maxDepth());

    List<ClosureFinding> findings = new ArrayList<>();
    for (String urn : closure.unresolved()) {
      log.info(
          "Closure validation: unresolved reference to {} reached by pipeline {}",
          Encode.forJava(urn),
          pipeline.getId());
      findings.add(ClosureFinding.notAvailable(pipeline.getId()));
    }
    List<String> resolved =
        closure.artifacts().stream().filter(urn -> !closure.unresolved().contains(urn)).toList();
    if (resolved.isEmpty()) {
      return findings;
    }
    Map<String, List<DataStructureVersion>> governed =
        recordsPinnedBy(resolved).stream()
            .collect(Collectors.groupingBy(DataStructureVersion::getModelUrn));
    for (String urn : resolved) {
      List<DataStructureVersion> records = governed.get(urn);
      if (records != null) {
        inspectGoverned(pipeline.getId(), urn, records, readability).ifPresent(findings::add);
      }
    }
    return findings;
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
   * Holds one governed artifact to what a released flow needs of it, in the order the disclosure
   * boundary requires: a caller who may not read it learns only that it is not available. Were its
   * draft state reported first, the reply would confirm the artifact exists and name a model from
   * another department.
   *
   * <p>A flow pins a registry artifact rather than one of the platform's records of it, and several
   * records can pin the same artifact — the registry returns the version it already holds when
   * content is byte-identical. The artifact can therefore carry a release as soon as one record the
   * caller may read says so.
   */
  private Optional<ClosureFinding> inspectGoverned(
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
      return Optional.of(ClosureFinding.notAvailable(pipelineId));
    }
    if (readable.stream().noneMatch(PipelineClosureValidator::isReleased)) {
      return Optional.of(ClosureFinding.notReleased(pipelineId, urn));
    }
    return Optional.empty();
  }

  /**
   * Whether the caller may read the structure carrying this version.
   *
   * <p>Decided here because OPA never sees a structure two hops away in the reference graph, and
   * the scope header it emits is typed to the route's own Data Set scope.
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
