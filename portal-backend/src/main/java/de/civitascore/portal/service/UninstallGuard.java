package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.InstalledArtifact;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides, before an uninstall changes anything, whether the artifacts of an installation can be
 * removed.
 *
 * <p>The services of the artifacts make the same decisions when they delete. The uninstall cannot
 * wait for them: the model registry keeps its dependency graph in memory, and a rollback does not
 * restore it. A refusal after the first removal would leave the graph without artifacts that the
 * database has again. The check thus comes first and reads only.
 *
 * <p>An artifact is in use when something that the installation did not create references it. The
 * references between the artifacts of the installation do not count, because the uninstall removes
 * both ends.
 */
@Component
@RequiredArgsConstructor
public class UninstallGuard {

  private static final String INSTALLATION = "Installation";
  private static final String DATA_SOURCE = "DataSource";
  private static final String DATA_STRUCTURE = "DataStructure";

  private final DataSetService dataSetService;
  private final DataSourceService dataSourceService;
  private final DataStructureService dataStructureService;
  private final PipelineRepository pipelineRepository;
  private final DataSourceRepository dataSourceRepository;
  private final ModelRegistryGateway modelRegistryGateway;

  /** What the installation created, in the forms that the references use. */
  private record Created(Set<UUID> dataSetIds, Set<UUID> dataSourceIds, Set<String> urns) {}

  /**
   * Refuses the uninstall if an artifact of the installation cannot be removed. An artifact that no
   * longer exists is not checked.
   *
   * @param installationId the id of the installation
   * @param lines the journal lines of the artifacts that the installation created
   * @throws SagaInFlightException if an operation on a Dataset of the installation is in progress
   * @throws ResourceInUseException if a Dataset of the installation is released or has
   *     infrastructure on the platform, or if something that the installation did not create uses
   *     one of its artifacts
   */
  @Transactional(readOnly = true)
  public void requireRemovable(UUID installationId, List<InstalledArtifact> lines) {
    Created created =
        new Created(
            shellIds(lines, InstalledArtifactType.DATA_SET),
            shellIds(lines, InstalledArtifactType.DATA_SOURCE),
            lines.stream()
                .map(InstalledArtifact::getUrn)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

    existing(created.dataSetIds(), dataSetService::findById)
        .forEach(dataSet -> requireNoInfrastructure(dataSet, installationId));
    existing(created.dataSourceIds(), dataSourceService::findById)
        .forEach(dataSource -> requireNotUsedElsewhere(dataSource, created));
    existing(shellIds(lines, InstalledArtifactType.DATA_STRUCTURE), dataStructureService::findById)
        .forEach(dataStructure -> requireNotUsedElsewhere(dataStructure, created));
  }

  /**
   * A Dataset can be removed in the same transaction only while it has no infrastructure. A
   * released Dataset serves data, and the removal of infrastructure is an operation of its own that
   * completes later. The operator does these steps, thus the uninstall refuses and says which.
   */
  private static void requireNoInfrastructure(DataSet dataSet, UUID installationId) {
    if (dataSet.getPendingSagaType() != null) {
      throw new SagaInFlightException(
          dataSet.getId(),
          dataSet.getPendingSagaType(),
          "Cannot uninstall while an operation on Dataset '%s' is in progress: %s"
              .formatted(dataSet.getName(), dataSet.getPendingSagaType()));
    }
    if (dataSet.getDataSetStatus() == DataSetStatus.AVAILABLE) {
      throw new ResourceInUseException(
          INSTALLATION,
          installationId,
          ("Cannot uninstall because Dataset '%s' is released. Unrelease the Dataset and delete"
                  + " it. Then uninstall again.")
              .formatted(dataSet.getName()));
    }
    if (dataSet.isProvisioned()) {
      throw new ResourceInUseException(
          INSTALLATION,
          installationId,
          ("Cannot uninstall because Dataset '%s' has infrastructure on the platform. Delete the"
                  + " Dataset. Then uninstall again.")
              .formatted(dataSet.getName()));
    }
  }

  /** A Pipeline of a Dataset that the installation did not create uses the Data source. */
  private void requireNotUsedElsewhere(DataSource dataSource, Created created) {
    List<String> pipelines =
        pipelineRepository.findByDataSourcesId(dataSource.getId()).stream()
            .filter(pipeline -> !created.dataSetIds().contains(pipeline.getDataSet().getId()))
            .map(pipeline -> pipeline.getId().toString())
            .toList();
    if (!pipelines.isEmpty()) {
      throw new ResourceInUseException(
          DATA_SOURCE,
          dataSource.getId(),
          ("Cannot uninstall because Data source '%s' is used by a Pipeline that the installation"
                  + " did not create.")
              .formatted(dataSource.getName()),
          pipelines);
    }
  }

  /**
   * A Data source pins a version by a portal column, which the model registry does not see. All
   * other references are in the registry.
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
                  + " installation did not create.")
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
                  + " installation did not create.")
              .formatted(dataStructure.getName()),
          referrers);
    }
  }

  /**
   * Whether the installation created the artifact behind a URN. A reference can come from an
   * Element, and the journal records the Data structure that contains it.
   */
  private boolean isCreated(String urn, Created created) {
    return created.urns().contains(modelRegistryGateway.logicalUrn(urn))
        || modelRegistryGateway.hostModelUrnsOf(urn).stream().anyMatch(created.urns()::contains);
  }

  private static Set<UUID> shellIds(List<InstalledArtifact> lines, InstalledArtifactType type) {
    return lines.stream()
        .filter(line -> line.getArtifactType() == type)
        .map(InstalledArtifact::getShellId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private static <T> List<T> existing(Set<UUID> ids, Function<UUID, Optional<T>> finder) {
    return ids.stream().map(finder).flatMap(Optional::stream).toList();
  }
}
