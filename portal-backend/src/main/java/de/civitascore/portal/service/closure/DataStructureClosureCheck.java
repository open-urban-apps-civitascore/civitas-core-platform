package de.civitascore.portal.service.closure;

import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Component;

/**
 * Holds a participating Data Structure to what a released flow needs of it: the platform must know
 * it, the caller must be allowed to read it, it must itself be released, and its model must still
 * compile.
 *
 * <p>The order the four are decided in is load-bearing. A caller who may not read a structure
 * learns only that it is not available; were its draft state or a schema diagnostic reported first,
 * the reply would confirm the structure exists and disclose a model from another department. So
 * nothing beyond {@link ClosureFinding.Reason#NOT_AVAILABLE} is said about a structure until the
 * caller is known to be entitled to it, and the reason it was withheld goes to the log alone.
 *
 * <p>A flow pins a registry artifact rather than one of the platform's records of it, and several
 * records can pin the same artifact. The artifact can therefore carry a release as soon as one
 * record the caller may read says so.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class DataStructureClosureCheck implements ClosureNodeCheck {

  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final ScopeAccessAuthorizer scopeAccessAuthorizer;
  private final ModelRegistryGateway modelRegistryGateway;

  @Override
  public String artifactType() {
    return "datastructure";
  }

  @Override
  public List<ClosureFinding> check(List<ClosureNode> nodes) {
    List<ClosureFinding> findings = new ArrayList<>();
    Map<ClosureNode, List<DataStructureVersion>> known = new LinkedHashMap<>();
    Set<UUID> referencedStructures = new LinkedHashSet<>();

    for (ClosureNode node : nodes) {
      List<DataStructureVersion> records =
          dataStructureVersionRepository.findAllByModelUrn(node.artifactUrn());
      if (records.isEmpty()) {
        // The registry holds the artifact but the platform has no lifecycle record of it, so
        // whether it may carry a release cannot be decided. Reported as the withheld reason.
        log.info(
            "Closure validation: no DataStructureVersion records {} reached by pipeline {}",
            Encode.forJava(node.artifactUrn()),
            node.pipelineId());
        findings.add(ClosureFinding.notAvailable(node.pipelineId(), node.artifactUrn()));
      } else {
        known.put(node, records);
        records.forEach(record -> referencedStructures.add(record.getDataStructure().getId()));
      }
    }

    Set<UUID> unauthorized =
        scopeAccessAuthorizer.unauthorizedReferences(
            ScopeType.DATASTRUCTURE, List.copyOf(referencedStructures));

    known.forEach((node, records) -> findings.addAll(inspect(node, records, unauthorized)));
    return findings;
  }

  /** The three checks that follow a resolved record, in the order the class javadoc requires. */
  private List<ClosureFinding> inspect(
      ClosureNode node, List<DataStructureVersion> records, Set<UUID> unauthorized) {
    List<DataStructureVersion> readable =
        records.stream()
            .filter(record -> !unauthorized.contains(record.getDataStructure().getId()))
            .toList();
    if (readable.isEmpty()) {
      log.warn(
          "Closure validation: caller may not read any data structure holding {} reached by"
              + " pipeline {}",
          Encode.forJava(node.artifactUrn()),
          node.pipelineId());
      return List.of(ClosureFinding.notAvailable(node.pipelineId(), node.artifactUrn()));
    }
    if (readable.stream().noneMatch(DataStructureClosureCheck::isReleased)) {
      return List.of(ClosureFinding.notReleased(node.pipelineId(), node.artifactUrn()));
    }
    Optional<ModelRegistryGateway.RegistryDocument> document =
        modelRegistryGateway.fetchModel(node.artifactUrn());
    if (document.isEmpty()) {
      // Registry reads take no transaction, so the artifact can go away between the walk
      // establishing it was there and this read. Absent is not the same as compiling cleanly.
      log.info(
          "Closure validation: no model to read for {} reached by pipeline {}",
          Encode.forJava(node.artifactUrn()),
          node.pipelineId());
      return List.of(ClosureFinding.notAvailable(node.pipelineId(), node.artifactUrn()));
    }
    List<String> diagnostics = modelRegistryGateway.validateSchema(document.get().content());
    return diagnostics.isEmpty()
        ? List.of()
        : List.of(ClosureFinding.invalid(node.pipelineId(), node.artifactUrn(), diagnostics));
  }

  /**
   * Whether a version counts as released. Both the version and the structure carrying it must be,
   * the same pair {@code DataSourceService} requires before a version may be linked — a released
   * version of a structure that is still a draft is not something a flow may publish.
   */
  private static boolean isReleased(DataStructureVersion version) {
    return version.getDataStructureVersionStatus() == DataStructureVersionStatus.AVAILABLE
        && version.getDataStructure().getDataStructureStatus() == DataStructureStatus.AVAILABLE;
  }
}
