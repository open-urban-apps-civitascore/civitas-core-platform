package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.BundleInstallation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.model.output.InstallationOutputDTO.InstalledArtifactOutputDTO;
import de.civitascore.portal.repository.BundleInstallationRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the install provenance — what was installed by whom, and what each install did —
 * plus the first slice of uninstall: structure-only installations. The artifact lines load via
 * {@code @BatchSize} on the entity, so paging stays a real database limit rather than an in-memory
 * cut.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstallationService {

  private final BundleInstallationRepository bundleInstallationRepository;
  private final DataStructureService dataStructureService;

  /**
   * A page of recorded installations; sort order comes from the pageable (default: newest first).
   */
  @Transactional(readOnly = true)
  public Page<InstallationOutputDTO> findAll(Pageable pageable) {
    return bundleInstallationRepository.findAll(pageable).map(InstallationService::toOutput);
  }

  /**
   * Uninstalls a structure-only installation: deletes every data structure this installation
   * CREATED (shell, versions and the backing registry model go through {@link
   * DataStructureService}'s regular delete path, so all its guards apply), then marks the
   * installation uninstalled. The journal is never rewritten — header and lines survive as history,
   * only the terminal timestamp is set.
   *
   * <p>Deliberately slice 1: any installation containing non-structure lines (a use-case bundle) is
   * refused with a clear message. Bundles need reverse-order teardown across all six artifact types
   * plus the upstream dataset-delete fix — see the uninstall plan in the meta repo.
   *
   * <p>Reference counting decides deletion: a structure another ACTIVE installation also references
   * (CREATED here, REUSED there) blocks the uninstall with 409 rather than pulling the artifact out
   * from under the other install. REUSED lines of THIS installation never delete anything — this
   * install did not create the artifact, so uninstalling only withdraws its claim.
   *
   * <p>Note: deletion happens under {@code INSTALLATION_DELETE} rather than {@code
   * DATASTRUCTURE_DELETE} — the same implicit-permission trade-off as the import's implicit
   * release, flagged as an upstream design question there.
   *
   * @param installationId the provenance record to uninstall
   * @throws ResourceNotFoundException if no such installation exists (404)
   * @throws InvalidInputException if already uninstalled, or not structure-only (400)
   * @throws ResourceInUseException if another active installation references a structure (409)
   */
  @Transactional
  public void uninstall(UUID installationId) {
    BundleInstallation installation =
        bundleInstallationRepository
            .findById(installationId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        BundleInstallation.class.getSimpleName(), installationId));

    if (installation.getUninstalledAt() != null) {
      throw new InvalidInputException(
          "Installation",
          installationId,
          "already uninstalled at " + installation.getUninstalledAt());
    }
    boolean structureOnly =
        installation.getArtifacts().stream()
            .allMatch(line -> line.getArtifactType() == InstalledArtifactType.DATA_STRUCTURE);
    if (!structureOnly) {
      throw new InvalidInputException(
          "Installation",
          installationId,
          "only structure-only installations can be uninstalled; use-case bundles need the"
              + " reverse-order teardown of a later increment");
    }

    for (InstalledArtifact line : installation.getArtifacts()) {
      if (line.getAction() != InstalledArtifactAction.CREATED) {
        // This install only borrowed the artifact; uninstalling withdraws the claim, no more.
        continue;
      }
      long claims =
          bundleInstallationRepository.countOtherActiveInstallationsReferencing(
              line.getUrn(), installationId);
      if (claims > 0) {
        throw new ResourceInUseException(
            "Installation",
            installationId,
            ("data structure '%s' is referenced by %d other active installation(s) and stays"
                    + " installed; uninstall those first")
                .formatted(line.getName(), claims));
      }
      if (line.getShellId() == null || !dataStructureService.existsById(line.getShellId())) {
        // Already removed by hand. The journal records history, reality has moved on — mark the
        // installation uninstalled rather than failing on work that is already done.
        log.info(
            "uninstall {}: structure '{}' (shell {}) already gone, skipping delete",
            installationId,
            line.getName(),
            line.getShellId());
        continue;
      }
      dataStructureService.deleteById(line.getShellId());
    }

    installation.setUninstalledAt(LocalDateTime.now());
    bundleInstallationRepository.save(installation);
  }

  private static InstallationOutputDTO toOutput(BundleInstallation installation) {
    InstallationOutputDTO output = new InstallationOutputDTO();
    output.setId(installation.getId());
    output.setCreatedAt(installation.getCreatedAt());
    output.setModifiedAt(installation.getModifiedAt());
    output.setUninstalledAt(installation.getUninstalledAt());
    output.setBundleId(installation.getBundleId());
    output.setBundleVersion(installation.getBundleVersion());
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
                        .action(artifact.getAction())
                        .build())
            .toList());
    return output;
  }
}
