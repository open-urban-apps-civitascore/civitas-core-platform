package de.civitascore.portal.service.closure;

import de.civitascore.portal.configuration.PipelineClosureValidationProperties;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.util.PipelineClosureTooLargeException;
import de.civitascore.portal.util.PipelineClosureValidationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
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
 * does not block it. The reference graph is the authority: pipeline content is never parsed,
 * matching how {@code DataSetSagaPublisher} learns the same thing.
 *
 * <p>Every artifact reached must resolve. Beyond that, what is asked of it depends on its kind, and
 * a {@link ClosureNodeCheck} answers for the kinds where more is required. A Data Structure carries
 * a release lifecycle and an authorization scope, so it is held to both; a member element has
 * neither of its own, and demanding one would mean choosing a structure to inherit from among
 * several that may share it — elements are deliberately reusable, so a shared element would
 * otherwise be blocked by any unrelated draft that also uses it.
 *
 * <p>Findings are collected across every flow and reported together, so one attempt names
 * everything that needs repairing.
 */
@Component
@Slf4j
public class PipelineClosureValidator {

  private final ModelRegistryGateway modelRegistryGateway;
  private final PipelineClosureValidationProperties properties;
  private final Map<String, ClosureNodeCheck> checksByArtifactType;

  public PipelineClosureValidator(
      ModelRegistryGateway modelRegistryGateway,
      PipelineClosureValidationProperties properties,
      List<ClosureNodeCheck> checks) {
    this.modelRegistryGateway = modelRegistryGateway;
    this.properties = properties;
    this.checksByArtifactType = new LinkedHashMap<>();
    checks.forEach(check -> this.checksByArtifactType.put(check.artifactType(), check));
  }

  /**
   * Validates the artifacts participating in every one of the Data Set's flows.
   *
   * @param pipelines the Data Set's pipelines; one with no stored model yet contributes no flow
   * @throws PipelineClosureTooLargeException if a flow reaches more artifacts than the configured
   *     bound, so that none of them were examined
   * @throws PipelineClosureValidationException if any participating artifact cannot carry a
   *     release, carrying every finding across every flow
   */
  public void validate(Collection<Pipeline> pipelines) {
    if (pipelines == null || pipelines.isEmpty()) {
      return;
    }

    List<PipelineClosureTooLargeException.OversizedClosure> oversized = new ArrayList<>();
    List<ClosureNode> nodes = new ArrayList<>();
    List<ClosureFinding> findings = new ArrayList<>();

    for (Pipeline pipeline : pipelines) {
      if (pipeline.getModelUrn() == null || pipeline.getModelUrn().isBlank()) {
        continue;
      }
      Set<String> closure =
          modelRegistryGateway.transitiveDependencyUrns(
              pipeline.getModelUrn(), properties.maxDepth());
      if (closure.size() > properties.maxArtifacts()) {
        oversized.add(
            new PipelineClosureTooLargeException.OversizedClosure(
                pipeline.getId(), closure.size(), properties.maxArtifacts()));
        continue;
      }
      closure.forEach(urn -> collect(pipeline, urn, nodes, findings));
    }

    // A bound was hit, so part of the model was never looked at. Reporting the findings from the
    // flows that were examined would read as the complete picture, which it is not.
    if (!oversized.isEmpty()) {
      throw new PipelineClosureTooLargeException(oversized);
    }

    findings.addAll(runChecks(nodes));

    if (!findings.isEmpty()) {
      throw new PipelineClosureValidationException(findings);
    }
  }

  /**
   * Sorts one reached artifact into a finding when it does not resolve, or into the nodes awaiting
   * their kind's checks when it does.
   *
   * <p>The registry keeps a reference whose target it no longer holds, deliberately — the link
   * still states what the document said. So a URN appearing in the closure is not itself proof the
   * artifact is there, and asking is what detects the artifact that went away.
   */
  private void collect(
      Pipeline pipeline, String urn, List<ClosureNode> nodes, List<ClosureFinding> findings) {
    if (!modelRegistryGateway.exists(urn)) {
      log.info(
          "Closure validation: unresolved reference to {} reached by pipeline {}",
          Encode.forJava(urn),
          pipeline.getId());
      findings.add(ClosureFinding.notAvailable(pipeline.getId(), urn));
      return;
    }
    nodes.add(new ClosureNode(pipeline.getId(), urn, modelRegistryGateway.artifactType(urn)));
  }

  /**
   * Hands each kind's nodes to its check in one call, so a check can decide for the whole set at
   * once rather than repeating per artifact what it could ask once.
   */
  private List<ClosureFinding> runChecks(List<ClosureNode> nodes) {
    Map<String, List<ClosureNode>> byArtifactType = new LinkedHashMap<>();
    for (ClosureNode node : nodes) {
      if (checksByArtifactType.containsKey(node.artifactType())) {
        byArtifactType.computeIfAbsent(node.artifactType(), type -> new ArrayList<>()).add(node);
      }
    }
    List<ClosureFinding> findings = new ArrayList<>();
    byArtifactType.forEach(
        (artifactType, typedNodes) ->
            findings.addAll(checksByArtifactType.get(artifactType).check(typedNodes)));
    return findings;
  }
}
