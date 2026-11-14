package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.UseCaseArtifactType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.UseCaseArtifact;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.SagaInFlightException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decides, before an uninstall changes anything, whether the artifacts of a use case can be
 * removed.
 *
 * <p>The services of the artifacts make the same decisions when they delete. The uninstall cannot
 * wait for them: Model Forge keeps its dependency graph in memory, and a rollback does not restore
 * the graph. A refusal after the first removal would leave the graph without artifacts that the
 * database has again. The check thus comes first and only reads.
 *
 * <p>An artifact is in use when something that the use case did not create references it. The
 * references between the artifacts of the use case do not count, because the uninstall removes both
 * ends.
 *
 * <p>The uninstall releases nothing and unreleases nothing, as the install releases nothing. Each
 * status transition has its own endpoint and its own permission. So a released artifact stops the
 * uninstall, and the message says which step the caller must do first.
 */
@Component
@RequiredArgsConstructor
public class UninstallGuard {

  private static final String USE_CASE = "UseCase";
  private static final String DATA_SOURCE = "DataSource";
  private static final String DATA_STRUCTURE = "DataStructure";

  private final DataSetService dataSetService;
  private final DataSourceService dataSourceService;
  private final DataStructureService dataStructureService;
  private final PipelineRepository pipelineRepository;
  private final DataSourceRepository dataSourceRepository;
  private final ModelRegistryGateway modelRegistryGateway;

  /** What the use case created, in the forms that the references use. */
  private record Created(Set<UUID> dataSetIds, Set<UUID> dataSourceIds, Set<String> urns) {}

  /**
   * Refuses the uninstall if an artifact of the use case cannot be removed. An artifact that no
   * longer exists is not checked.
   *
   * @param useCaseId the id of the use case
   * @param lines the lines of the use case
   * @throws SagaInFlightException if an operation on a Dataset of the use case is in progress
   * @throws ResourceInUseException if a Dataset of the use case is released or has infrastructure
   *     on the platform, if a Data source of the use case is released, or if something that the use
   *     case did not create uses one of its artifacts
   */
  public void requireRemovable(UUID useCaseId, List<UseCaseArtifact> lines) {
    Created created =
        new Created(
            shellIds(lines, UseCaseArtifactType.DATA_SET),
            shellIds(lines, UseCaseArtifactType.DATA_SOURCE),
            lines.stream()
                .map(UseCaseArtifact::getUrn)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

    existing(created.dataSetIds(), dataSetService::findById)
        .forEach(dataSet -> requireNoInfrastructure(dataSet, useCaseId));
    existing(created.dataSourceIds(), dataSourceService::findById)
        .forEach(dataSource -> requireRemovable(dataSource, created));
    existing(shellIds(lines, UseCaseArtifactType.DATA_STRUCTURE), dataStructureService::findById)
        .forEach(dataStructure -> requireNotUsedElsewhere(dataStructure, created));
  }

  /**
   * A Dataset can be removed in the same transaction only while it has no infrastructure. A
   * released Dataset serves data, and the removal of infrastructure is an operation of its own that
   * completes later. The caller does these steps through the Dataset endpoints, so the uninstall
   * refuses and says which.
   */
  private static void requireNoInfrastructure(DataSet dataSet, UUID useCaseId) {
    if (dataSet.getPendingSagaType() != null) {
      throw new SagaInFlightException(
          dataSet.getId(),
          dataSet.getPendingSagaType(),
          "Cannot uninstall while an operation on Dataset '%s' is in progress: %s"
              .formatted(dataSet.getName(), dataSet.getPendingSagaType()));
    }
    if (dataSet.getDataSetStatus() == DataSetStatus.AVAILABLE) {
      throw new ResourceInUseException(
          USE_CASE,
          useCaseId,
          ("Cannot uninstall because Dataset '%s' is released. Unrelease the Dataset and delete"
                  + " it. Then uninstall again.")
              .formatted(dataSet.getName()));
    }
    if (dataSet.isProvisioned()) {
      throw new ResourceInUseException(
          USE_CASE,
          useCaseId,
          ("Cannot uninstall because Dataset '%s' has infrastructure on the platform. Delete the"
                  + " Dataset. Then uninstall again.")
              .formatted(dataSet.getName()));
    }
  }

  /**
   * The platform deletes only a Data source that is not released. A Pipeline of a Dataset that the
   * use case did not create must not use it.
   */
  private void requireRemovable(DataSource dataSource, Created created) {
    if (dataSource.getDataSourceStatus() == DataSourceStatus.AVAILABLE) {
      throw new ResourceInUseException(
          DATA_SOURCE,
          dataSource.getId(),
          ("Cannot uninstall because Data source '%s' is released. Unrelease the Data source."
                  + " Then uninstall again.")
              .formatted(dataSource.getName()));
    }
    List<String> pipelines =
        pipelineRepository.findByDataSourcesId(dataSource.getId()).stream()
            .filter(pipeline -> !created.dataSetIds().contains(pipeline.getDataSet().getId()))
            .map(pipeline -> pipeline.getId().toString())
            .toList();
    if (!pipelines.isEmpty()) {
      throw new ResourceInUseException(
          DATA_SOURCE,
          dataSource.getId(),
          ("Cannot uninstall because Data source '%s' is used by a Pipeline that the use case"
                  + " did not create.")
              .formatted(dataSource.getName()),
          pipelines);
    }
  }

  /**
   * A Data source pins a version by a column of the portal, which the registry does not see. The
   * registry knows all other references.
   */
  private void requireNotUsedElsewhere(DataStructure dataStructure, Created created) {
    Set<DataStructureVersion> versions = dataStructure.getDataStructureVersions();
    if (versions.isEmpty()) {
      return;
    }
    Set<UUID> versionIds =
        versions.stream().map(DataStructureVersion::getId).collect(Collectors.toSet());
    List<String> dataSources =
        Stream.of(DataSourceStatus.values())
            .map(
                status ->
                    dataSourceRepository.findIdsByDataStructureVersionIdInAndStatus(
                        versionIds, status))
            .flatMap(Collection::stream)
            .filter(id -> !created.dataSourceIds().contains(id))
            .map(UUID::toString)
            .toList();
    if (!dataSources.isEmpty()) {
      throw new ResourceInUseException(
          DATA_STRUCTURE,
          dataStructure.getId(),
          ("Cannot uninstall because Data structure '%s' is used by a Data source that the"
                  + " use case did not create.")
              .formatted(dataStructure.getName()),
          dataSources);
    }

    List<String> referrers =
        versions.stream()
            .map(DataStructureVersion::getModelUrn)
            .map(modelRegistryGateway::referencesTo)
            .flatMap(Collection::stream)
            .distinct()
            .filter(referrer -> !isCreated(referrer, created))
            .toList();
    if (!referrers.isEmpty()) {
      throw new ResourceInUseException(
          DATA_STRUCTURE,
          dataStructure.getId(),
          ("Cannot uninstall because Data structure '%s' is used by an artifact that the"
                  + " use case did not create.")
              .formatted(dataStructure.getName()),
          referrers);
    }
  }

  /**
   * Whether the use case created the artifact behind a URN. A reference can come from an Element,
   * and the record names the Data structure that contains it.
   */
  private boolean isCreated(String urn, Created created) {
    return created.urns().contains(modelRegistryGateway.logicalUrn(urn))
        || modelRegistryGateway.hostModelUrnsOf(urn).stream().anyMatch(created.urns()::contains);
  }

  private static Set<UUID> shellIds(List<UseCaseArtifact> lines, UseCaseArtifactType type) {
    return lines.stream()
        .filter(line -> line.getArtifactType() == type)
        .map(UseCaseArtifact::getShellId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private static <T> List<T> existing(Set<UUID> ids, Function<UUID, Optional<T>> finder) {
    return ids.stream().map(finder).flatMap(Optional::stream).toList();
  }
}
