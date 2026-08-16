package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.BundleInstallation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.repository.BundleInstallationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the install provenance: one header per install plus one line per artifact it touched. Used
 * by every import path — the bundle import and the single-structure import — so the record looks
 * the same no matter which endpoint produced it.
 *
 * <p>Always called inside the importing transaction: the record must exist exactly iff the install
 * committed. Lines are built by the caller from its DOMAIN results, never from the response DTO, so
 * a cosmetic response change can never alter recorded history.
 */
@Service
@RequiredArgsConstructor
public class InstallationRecorder {

  private final BundleInstallationRepository bundleInstallationRepository;

  /**
   * Records one install.
   *
   * @param bundleId catalogue identity of the bundle, verbatim; null when the caller declares none
   * @param bundleVersion catalogue version, verbatim; null when the caller declares none
   * @param dataSetId the produced dataset, or null for installs that produce none
   * @param dataSetName its name at install time, or null
   * @param lines one per touched artifact, in the order they were touched
   * @return the saved installation, with its assigned id
   */
  @Transactional
  public BundleInstallation record(
      String bundleId,
      String bundleVersion,
      UUID dataSetId,
      String dataSetName,
      List<InstalledArtifact> lines) {
    BundleInstallation installation = new BundleInstallation();
    installation.setBundleId(bundleId);
    installation.setBundleVersion(bundleVersion);
    installation.setDataSetId(dataSetId);
    installation.setDataSetName(dataSetName);
    lines.forEach(installation::addArtifact);
    return bundleInstallationRepository.save(installation);
  }

  /**
   * One provenance line. Which of {@code shellId} and {@code urn} is set says which layer the
   * artifact lives in: a data structure has both, a data source only a shell row, a mapping only a
   * registry identity.
   */
  public static InstalledArtifact line(
      InstalledArtifactType type,
      String name,
      UUID shellId,
      String urn,
      InstalledArtifactAction action) {
    InstalledArtifact artifact = new InstalledArtifact();
    artifact.setArtifactType(type);
    artifact.setName(name);
    artifact.setShellId(shellId);
    artifact.setUrn(urn);
    artifact.setAction(action);
    return artifact;
  }
}
