package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.input.InstallationInputDTO;
import de.civitascore.portal.model.input.PackageManifestInputDTO;
import de.civitascore.portal.model.input.PackageMemberInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Installs one package on this instance and records what it did.
 *
 * <p>Everything happens in one transaction, so a package is installed whole or not at all — and the
 * journal entry exists exactly when the install committed. Everything is created in DRAFT:
 * releasing stays the existing, separately permissioned action, so an install never grants more
 * than it was asked for.
 *
 * <p>A package fixes the URNs of its artifacts, which is what makes it reproducible across
 * instances — and is also why it can be installed only once per instance. Within a package a member
 * whose identity is already present with identical content is reused rather than duplicated; the
 * same identity with different content is refused.
 */
@Service
@RequiredArgsConstructor
public class InstallationService {

  private final InstallationRepository installationRepository;
  private final DataStructureService dataStructureService;
  private final DataStructureVersionService dataStructureVersionService;
  private final DataStructureRepository dataStructureRepository;
  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final ModelRegistryGateway modelRegistryGateway;

  @Transactional
  public Installation install(InstallationInputDTO input) {
    PackageManifestInputDTO manifest = input.getPackageManifest();
    if (installationRepository.existsByPackageId(manifest.getId())) {
      throw new UniqueConstraintViolationException(
          ("Package '%s' is already installed on this instance. A package fixes the URNs of its"
                  + " artifacts, so a second install would collide with the first; updating an"
                  + " installation is not supported yet.")
              .formatted(manifest.getId()));
    }

    Installation installation = new Installation();
    installation.setPackageId(manifest.getId());
    installation.setPackageVersion(manifest.getVersion());

    // Members are processed in list order. That is enough while data structures are the only kind:
    // they reference nothing inside the package. The kinds that do reference each other arrive with
    // the dependency ordering derived from those references, not from this list.
    for (PackageMemberInputDTO member : manifest.getMembers()) {
      installation.addArtifact(
          switch (member.getKind()) {
            case DATASTRUCTURE -> installDataStructure(member);
            default ->
                throw new InvalidInputException(
                    "kind",
                    member.getUrn(),
                    "Member kind '%s' is not installable yet; this increment covers data structures."
                        .formatted(member.getKind()));
          });
    }
    return installationRepository.save(installation);
  }

  @Transactional(readOnly = true)
  public Page<Installation> findAll(Pageable pageable) {
    return installationRepository.findAllByOrderByCreatedAtDesc(pageable);
  }

  @Transactional(readOnly = true)
  public Installation findByIdOrThrow(UUID id) {
    return installationRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Installation", id.toString()));
  }

  /**
   * Creates the data structure, or resolves it against one already installed. "Identical" means the
   * portable model: UI layout is instance-authored presentation and is ignored, so a diagram
   * rearranged in this instance's editor never blocks a reuse.
   */
  private InstalledArtifact installDataStructure(PackageMemberInputDTO member) {
    String logicalUrn = requireDataStructureIdentity(member);

    Optional<DataStructureVersion> installed =
        dataStructureVersionRepository.findFirstByModelUrnStartingWith(logicalUrn + ":");
    if (installed.isPresent()) {
      DataStructureVersion version = installed.get();
      if (!modelRegistryGateway.isUnchangedIgnoringUiStyles(
          version.getModelUrn(), member.getContent())) {
        throw new UniqueConstraintViolationException(
            ("A data structure for '%s' is already installed with different content. An install"
                    + " never overwrites; updating an installation is not supported yet.")
                .formatted(logicalUrn));
      }
      return line(member, version, InstalledArtifactAction.REUSED);
    }

    // No pinned version found. The shell turnstile still applies: a shell without a resolvable
    // version pin — a half-finished draft in the editor, say — must conflict rather than gain a
    // twin that points at the same identity.
    if (dataStructureRepository.existsByModelLogicalUrn(logicalUrn)) {
      throw new UniqueConstraintViolationException(
          ("A data structure for '%s' is already present without a resolvable version. Installing"
                  + " would create a duplicate shell.")
              .formatted(logicalUrn));
    }
    return line(member, create(member), InstalledArtifactAction.CREATED);
  }

  private DataStructureVersion create(PackageMemberInputDTO member) {
    DataStructureInputDTO structureInput = new DataStructureInputDTO();
    structureInput.setName(member.getName());
    structureInput.setDescription(member.getDescription());
    DataStructure structure = dataStructureService.create(structureInput);

    DataStructureVersionInputDTO versionInput = new DataStructureVersionInputDTO();
    versionInput.setDataStructureId(structure.getId());
    versionInput.setDataStructureVersionSource(DataStructureVersionSource.OWN);
    versionInput.setDescription(member.getDescription());
    versionInput.setModelName(member.getName());
    versionInput.setModel(member.getContent());
    return dataStructureVersionService.create(versionInput);
  }

  /**
   * The member's declared URN, checked to be a data structure identity and to agree with the one
   * the model carries. The registry types a stored schema from its {@code $id} alone, so a model
   * without a datastructure URN would silently land as a plain Element; and a model naming a
   * different identity than its manifest entry is a package that contradicts itself.
   */
  private String requireDataStructureIdentity(PackageMemberInputDTO member) {
    if (!modelRegistryGateway.isDataStructureUrn(member.getUrn())) {
      throw new InvalidInputException(
          "urn",
          member.getUrn(),
          "A data structure member must declare a CORE URN of artifact type 'datastructure'.");
    }
    String declared = modelRegistryGateway.logicalUrn(member.getUrn());
    Object carried = member.getContent().get("$id");
    if (carried instanceof String id
        && !id.isBlank()
        && !declared.equals(modelRegistryGateway.logicalUrn(id))) {
      throw new InvalidInputException(
          "$id",
          member.getUrn(),
          "The model's '$id' is '%s', which is not the member's urn '%s'."
              .formatted(id, declared));
    }
    return declared;
  }

  private InstalledArtifact line(
      PackageMemberInputDTO member, DataStructureVersion version, InstalledArtifactAction action) {
    InstalledArtifact artifact = new InstalledArtifact();
    artifact.setArtifactType(InstalledArtifactType.DATA_STRUCTURE);
    artifact.setName(member.getName());
    artifact.setShellId(version.getDataStructure().getId());
    artifact.setUrn(modelRegistryGateway.logicalUrn(version.getModelUrn()));
    artifact.setVersionedUrn(version.getModelUrn());
    artifact.setAction(action);
    return artifact;
  }
}
