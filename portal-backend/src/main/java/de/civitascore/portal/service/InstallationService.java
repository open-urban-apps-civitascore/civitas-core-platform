package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.model.output.InstallationOutputDTO.InstalledArtifactOutputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the install provenance — what was installed by whom, and what each install did —
 * plus uninstall: the teardown of everything an installation CREATED, in reverse touch order. The
 * artifact lines load via {@code @BatchSize} on the entity, so paging stays a real database limit
 * rather than an in-memory cut.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstallationService {

  private final InstallationRepository installationRepository;
  private final DataStructureService dataStructureService;
  private final DataSourceService dataSourceService;
  private final DataSinkService dataSinkService;
  private final PipelineService pipelineService;
  private final DataSetService dataSetService;
  private final MappingService mappingService;
  private final ModelRegistryGateway modelRegistryGateway;

  /**
   * A page of recorded installations; sort order comes from the pageable (default: newest first).
   */
  @Transactional(readOnly = true)
  public Page<InstallationOutputDTO> findAll(Pageable pageable) {
    return installationRepository.findAll(pageable).map(InstallationService::toOutput);
  }

  /**
   * Uninstalls an installation: tears down everything it CREATED, in reverse touch order —
   * pipelines, sinks, the dataset, mappings, sources, structures — each through its domain
   * service's regular delete path, so every guard applies. Then the journal's one permitted
   * amendment is written: the terminal {@code uninstalledAt}. Header and lines survive as history.
   *
   * <p>The reverse of the recorded order is not cosmetic: it is the dependency order backwards. A
   * pipeline can only be deleted while it exists on a DRAFT dataset and before its sinks go; a
   * mapping only once no manifest or pipeline references it; a structure only once no source, sink
   * or mapping holds it.
   *
   * <p>Package-database semantics decide what is deleted: REUSED lines never delete (this install
   * only withdraws its claim), and a CREATED artifact that another ACTIVE installation still
   * references is KEPT — like a shared package dependency, it disappears only when the last claim
   * goes. Artifacts already removed by hand are tolerated: the journal records history, reality has
   * moved on.
   *
   * <p>Two deliberate mitigations of upstream behaviour: {@code DataSetService.deleteById} bypasses
   * its own {@code postDelete} (it deletes via repository in {@code deleteWithSinks}), so the
   * dataset's registry manifest is checked and removed explicitly afterwards. And bundle sources
   * were released by the import, so they are unreleased here before deletion — the mirror of the
   * import's implicit release, with the same upstream design note.
   *
   * <p>Not yet: released/provisioned datasets. Their teardown runs through the asynchronous
   * DELETE/UNRELEASE sagas and is a later increment — refused with a clear message.
   *
   * @param installationId the provenance record to uninstall
   * @throws ResourceNotFoundException if no such installation exists (404)
   * @throws InvalidInputException if already uninstalled, or the dataset is not tearable (400)
   */
  @Transactional
  public void uninstall(UUID installationId) {
    Installation installation =
        installationRepository
            .findById(installationId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        Installation.class.getSimpleName(), installationId));

    if (installation.getUninstalledAt() != null) {
      throw new InvalidInputException(
          "Installation",
          installationId,
          "already uninstalled at " + installation.getUninstalledAt());
    }
    requireTearableDataSet(installation);

    for (InstalledArtifact line : installation.getArtifacts().reversed()) {
      if (line.getAction() != InstalledArtifactAction.CREATED) {
        log.info(
            "uninstall {}: {} '{}' was reused, withdrawing the claim only",
            installationId,
            line.getArtifactType(),
            line.getName());
        continue;
      }
      if (line.getUrn() != null) {
        long claims =
            installationRepository.countOtherActiveInstallationsReferencing(
                line.getUrn(), installationId);
        if (claims > 0) {
          // Shared-dependency semantics: the artifact outlives this install because someone
          // else's claim is still active. It goes when the last claim goes.
          log.info(
              "uninstall {}: keeping {} '{}' — {} other active installation(s) reference it",
              installationId,
              line.getArtifactType(),
              line.getName(),
              claims);
          continue;
        }
      }
      switch (line.getArtifactType()) {
        case PIPELINE -> deleteShellIfPresent(pipelineService, line, installationId);
        case DATA_SINK -> deleteShellIfPresent(dataSinkService, line, installationId);
        case DATA_SET -> {
          deleteShellIfPresent(dataSetService, line, installationId);
          removeOrphanedManifest(line, installationId);
        }
        case MAPPING -> deleteMappingArtifact(line, installationId);
        case DATA_SOURCE -> deleteSource(line, installationId);
        case DATA_STRUCTURE -> deleteShellIfPresent(dataStructureService, line, installationId);
      }
    }

    installation.setUninstalledAt(LocalDateTime.now());
    installationRepository.save(installation);
  }

  /**
   * The teardown depends on the dataset being in its direct-delete corridor: DRAFT, never
   * provisioned, no saga in flight. Everything else needs the asynchronous unrelease/delete sagas —
   * a later increment, refused honestly instead of half-done.
   */
  private void requireTearableDataSet(Installation installation) {
    installation.getArtifacts().stream()
        .filter(line -> line.getArtifactType() == InstalledArtifactType.DATA_SET)
        .filter(line -> line.getAction() == InstalledArtifactAction.CREATED)
        .filter(line -> line.getShellId() != null)
        .map(line -> dataSetService.findById(line.getShellId()))
        .flatMap(Optional::stream)
        .forEach(
            dataSet -> {
              if (dataSet.getPendingSagaType() != null) {
                throw new InvalidInputException(
                    "Installation",
                    installation.getId(),
                    "a saga is in flight for dataset '%s' (%s); retry once it completed"
                        .formatted(dataSet.getName(), dataSet.getPendingSagaType()));
              }
              if (dataSet.isProvisioned()
                  || dataSet.getDataSetStatus() == DataSetStatus.AVAILABLE) {
                throw new InvalidInputException(
                    "Installation",
                    installation.getId(),
                    ("dataset '%s' is released/provisioned; unrelease it first — asynchronous"
                            + " infrastructure teardown is a later increment")
                        .formatted(dataSet.getName()));
              }
              if (dataSet.getDataSetStatus() != DataSetStatus.DRAFT) {
                throw new InvalidInputException(
                    "Installation",
                    installation.getId(),
                    "dataset '%s' is %s; unstage it to DRAFT first"
                        .formatted(dataSet.getName(), dataSet.getDataSetStatus()));
              }
            });
  }

  /** Deletes a shell row through its domain service, tolerating one already removed by hand. */
  private void deleteShellIfPresent(
      BaseService<?, ?> service, InstalledArtifact line, UUID installationId) {
    if (line.getShellId() == null || !service.existsById(line.getShellId())) {
      log.info(
          "uninstall {}: {} '{}' (shell {}) already gone, skipping delete",
          installationId,
          line.getArtifactType(),
          line.getName(),
          line.getShellId());
      return;
    }
    service.deleteById(line.getShellId());
  }

  /**
   * Upstream mitigation: {@code DataSetService.deleteById} deletes the row via repository and never
   * runs its own {@code postDelete}, orphaning the registry manifest (meta repo, delete-path
   * finding 1 — cause identified in the deleteWithSinks bypass). Verify and repair: if the manifest
   * still exists after the shell delete, remove it explicitly. Members are kept by design; Model
   * Forge drops the manifest's outgoing dataset-ref edges.
   */
  private void removeOrphanedManifest(InstalledArtifact line, UUID installationId) {
    String manifestUrn = line.getUrn();
    if (manifestUrn == null || modelRegistryGateway.fetchPayload(manifestUrn).isEmpty()) {
      return;
    }
    log.warn(
        "uninstall {}: dataset delete left its manifest '{}' behind (upstream postDelete bypass),"
            + " removing it explicitly",
        installationId,
        manifestUrn);
    modelRegistryGateway.deleteDataSet(manifestUrn);
  }

  /**
   * A mapping is registry-only: no shell to delete, the artifact itself goes. By this point the
   * manifest and the pipelines that referenced it are gone, so the plain (non-force) delete applies
   * — a remaining reference would rightly refuse.
   */
  private void deleteMappingArtifact(InstalledArtifact line, UUID installationId) {
    if (line.getUrn() == null || modelRegistryGateway.fetchPayload(line.getUrn()).isEmpty()) {
      log.info(
          "uninstall {}: mapping '{}' already gone, skipping delete",
          installationId,
          line.getName());
      return;
    }
    mappingService.delete(line.getUrn(), false);
  }

  /**
   * The mirror of the import's implicit release: bundle sources were released so pipelines could
   * link them, and a released source refuses deletion — so unrelease first. Same upstream design
   * note as the release side.
   */
  private void deleteSource(InstalledArtifact line, UUID installationId) {
    if (line.getShellId() == null) {
      return;
    }
    DataSource source = dataSourceService.findById(line.getShellId()).orElse(null);
    if (source == null) {
      log.info(
          "uninstall {}: source '{}' (shell {}) already gone, skipping delete",
          installationId,
          line.getName(),
          line.getShellId());
      return;
    }
    if (source.getDataSourceStatus() == DataSourceStatus.AVAILABLE) {
      dataSourceService.unrelease(source.getId());
    }
    dataSourceService.deleteById(source.getId());
  }

  private static InstallationOutputDTO toOutput(Installation installation) {
    InstallationOutputDTO output = new InstallationOutputDTO();
    output.setId(installation.getId());
    output.setCreatedAt(installation.getCreatedAt());
    output.setModifiedAt(installation.getModifiedAt());
    output.setUninstalledAt(installation.getUninstalledAt());
    output.setCatalogEntryId(installation.getCatalogEntryId());
    output.setCatalogEntryVersion(installation.getCatalogEntryVersion());
    output.setDataSetId(installation.getDataSetId());
    output.setDataSetName(installation.getDataSetName());
    output.setInstalledBy(installation.getCreatedBy());
    output.setArtifacts(
        installation.getArtifacts().stream()
            .map(
                artifact ->
                    InstalledArtifactOutputDTO.builder()
                        .artifactType(artifact.getArtifactType())
                        .name(artifact.getName())
                        .shellId(artifact.getShellId())
                        .urn(artifact.getUrn())
                        .versionedUrn(artifact.getVersionedUrn())
                        .action(artifact.getAction())
                        .build())
            .toList());
    return output;
  }
}
