package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.repository.InstallationRepository;
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

  private final InstallationRepository installationRepository;

  /**
   * Records one install.
   *
   * @param catalogEntryId catalogue identity of the installed entry, verbatim; null when the caller
   *     declares none
   * @param catalogEntryVersion catalogue version, verbatim; null when the caller declares none
   * @param dataSetId the produced dataset, or null for installs that produce none
   * @param dataSetName its name at install time, or null
   * @param lines one per touched artifact, in the order they were touched
   * @return the saved installation, with its assigned id
   */
  @Transactional
  public Installation record(
      String catalogEntryId,
      String catalogEntryVersion,
      UUID dataSetId,
      String dataSetName,
      List<InstalledArtifact> lines) {
    Installation installation = new Installation();
    installation.setCatalogEntryId(catalogEntryId);
    installation.setCatalogEntryVersion(catalogEntryVersion);
    installation.setDataSetId(dataSetId);
    installation.setDataSetName(dataSetName);
    lines.forEach(installation::addArtifact);
    return installationRepository.save(installation);
  }

  /**
   * One provenance line. Which of {@code shellId} and {@code urn} is set says which layer the
   * artifact lives in: a data structure has both, a data source only a shell row, a mapping only a
   * registry identity. {@code versionedUrn} additionally records WHICH version this install
   * resolved — the logical {@code urn} stays the stable identity reference counting keys on.
   */
  public static InstalledArtifact line(
      InstalledArtifactType type,
      String name,
      UUID shellId,
      String urn,
      String versionedUrn,
      InstalledArtifactAction action) {
    InstalledArtifact artifact = new InstalledArtifact();
    artifact.setArtifactType(type);
    artifact.setName(name);
    artifact.setShellId(shellId);
    artifact.setUrn(urn);
    artifact.setVersionedUrn(versionedUrn);
    artifact.setAction(action);
    return artifact;
  }
}
