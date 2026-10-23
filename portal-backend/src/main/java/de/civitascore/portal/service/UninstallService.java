package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes what an installation created and keeps its record.
 *
 * <p>The uninstall first makes sure that each artifact can be removed ({@link UninstallGuard}), and
 * it changes nothing if one cannot. Then it removes the artifacts in the reverse order of the
 * install: first the Dataset, then the Data sources, then the Data structures. Each removal goes
 * through the service of its artifact, so the rules of the platform apply. All changes are in one
 * transaction.
 *
 * <p>The Dataset goes with all that it contains: its Pipelines, Mappings, Data sinks, named APIs,
 * styles and layers, also the ones that a user added after the install. The Data pool stays,
 * because the install did not create it.
 *
 * <p>An artifact that no longer exists is skipped, because a user can delete it by hand. Thus a
 * caller that removes the Dataset first, for example to remove its infrastructure, can then
 * uninstall.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UninstallService {

  private final InstallationRepository installationRepository;
  private final DataSetService dataSetService;
  private final DataSourceService dataSourceService;
  private final DataStructureService dataStructureService;
  private final UninstallGuard uninstallGuard;

  /**
   * Removes the artifacts of an installation and records the time of the uninstall. An installation
   * that is already uninstalled stays as it is.
   *
   * @param id the id of the installation
   * @throws ResourceNotFoundException if the installation does not exist
   * @throws ResourceInUseException if a Dataset of the installation is released or has
   *     infrastructure on the platform, if a Data source of the installation is released, or if
   *     something that the installation did not create uses one of its artifacts
   * @throws de.civitascore.portal.util.SagaInFlightException if an operation on a Dataset of the
   *     installation is in progress
   */
  @Transactional
  public void uninstall(UUID id) {
    Installation installation =
        installationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Installation", id.toString()));
    if (installation.isUninstalled()) {
      return;
    }
    List<InstalledArtifact> lines = installation.getArtifacts();
    uninstallGuard.requireRemovable(id, lines);

    lines.reversed().forEach(this::remove);

    installation.setUninstalledAt(LocalDateTime.now());
    installationRepository.save(installation);
    log.info(
        "Uninstalled installation {} of package {}",
        id,
        Encode.forJava(installation.getPackageId()));
  }

  /**
   * Removes the artifact of one line. The Dataset removes its Mappings, Data sinks, Pipelines,
   * named APIs, styles and layers, so their lines need no step of their own. The guard refused the
   * known cases before the first removal, so a refusal here is not usual. It names the artifact,
   * because the message of the service does not, and a package can have more than one artifact of a
   * kind.
   */
  private void remove(InstalledArtifact line) {
    try {
      switch (line.getArtifactType()) {
        case DATA_SET ->
            removeIfExists(line, dataSetService::existsById, dataSetService::deleteById);
        case DATA_SOURCE ->
            removeIfExists(line, dataSourceService::existsById, dataSourceService::deleteById);
        case DATA_STRUCTURE ->
            removeIfExists(
                line, dataStructureService::existsById, dataStructureService::deleteById);
        case NAMED_API, MAPPING, DATA_SINK, PIPELINE, STYLE, LAYER -> {
          // The Dataset removes them.
        }
      }
    } catch (ResourceInUseException e) {
      throw new ResourceInUseException(
          e.getResourceType(),
          e.getResourceId(),
          "Cannot uninstall because '%s' is in use. %s".formatted(line.getName(), e.getMessage()),
          e.getBlockedBy());
    }
  }

  private static void removeIfExists(
      InstalledArtifact line, Predicate<UUID> exists, Consumer<UUID> delete) {
    if (!exists.test(line.getShellId())) {
      log.info(
          "Uninstall skips {} {}, because it no longer exists",
          line.getArtifactType(),
          line.getShellId());
      return;
    }
    delete.accept(line.getShellId());
  }
}
