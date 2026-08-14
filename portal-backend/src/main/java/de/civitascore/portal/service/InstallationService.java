package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.BundleInstallation;
import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.model.output.InstallationOutputDTO.InstalledArtifactOutputDTO;
import de.civitascore.portal.repository.BundleInstallationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the install provenance: what was installed by whom, and what each install did. The
 * artifact lines load via {@code @BatchSize} on the entity, so paging stays a real database limit
 * rather than an in-memory cut.
 */
@Service
@RequiredArgsConstructor
public class InstallationService {

  private final BundleInstallationRepository bundleInstallationRepository;

  /**
   * A page of recorded installations; sort order comes from the pageable (default: newest first).
   */
  @Transactional(readOnly = true)
  public Page<InstallationOutputDTO> findAll(Pageable pageable) {
    return bundleInstallationRepository.findAll(pageable).map(InstallationService::toOutput);
  }

  private static InstallationOutputDTO toOutput(BundleInstallation installation) {
    InstallationOutputDTO output = new InstallationOutputDTO();
    output.setId(installation.getId());
    output.setCreatedAt(installation.getCreatedAt());
    output.setModifiedAt(installation.getModifiedAt());
    output.setBundleUrn(installation.getBundleUrn());
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
