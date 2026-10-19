package de.civitascore.portal.service;

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
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Installs a package on this instance and keeps a record of the install.
 *
 * <p>For each member, the install creates a copy on this instance. The backend gives each copy its
 * own URN. The record keeps the identity of the member in the package as the origin of the copy.
 * The install does not release what it creates: each Data structure stays DRAFT.
 *
 * <p>The install makes all changes to the database in one transaction. If one member fails, the
 * install keeps nothing. Model Forge keeps its dependency graph in memory, and a rollback does not
 * restore the graph. For this reason, the install does all checks before the first change to the
 * registry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstallationService {

  private static final String ID = "$id";
  private static final String TITLE = "title";
  private static final String DESCRIPTION = "description";
  private static final String NAME = "name";

  private final InstallationRepository installationRepository;
  private final DataStructureService dataStructureService;
  private final DataStructureVersionService dataStructureVersionService;
  private final ModelRegistryGateway modelRegistryGateway;

  /** A member that passed the checks, with the name and the description that the copy gets. */
  private record PreparedMember(PackageMemberInputDTO member, String name, String description) {}

  /**
   * Installs a package and returns the record of the install.
   *
   * @param input the package to install
   * @return the record, with one line for each artifact that the install created
   * @throws UniqueConstraintViolationException if this instance has an installation of the package
   * @throws org.springframework.dao.DataIntegrityViolationException if an install of the same
   *     package that runs at the same time commits first
   * @throws InvalidInputException if a member has no name or no description, or if the platform
   *     refuses the model of a member
   */
  @Transactional
  public Installation install(InstallationInputDTO input) {
    PackageManifestInputDTO manifest = input.getPackageManifest();
    if (installationRepository.existsByPackageId(manifest.getId())) {
      throw alreadyInstalled(manifest.getId());
    }
    List<PreparedMember> members =
        manifest.getMembers().stream().map(InstallationService::prepare).toList();

    Installation installation = new Installation();
    installation.setPackageId(manifest.getId());
    installation.setPackageVersion(manifest.getVersion());
    claim(installation);

    for (PreparedMember member : members) {
      installation.addArtifact(
          switch (member.member().getKind()) {
            case DATASTRUCTURE -> installDataStructure(member);
          });
    }
    Installation saved = installationRepository.save(installation);
    log.info(
        "Installed package {} as installation {}",
        Encode.forJava(saved.getPackageId()),
        saved.getId());
    return saved;
  }

  /**
   * Returns a page of installations.
   *
   * @param pageable the page and the sort order
   * @return the installations of the page, each with its lines
   */
  @Transactional(readOnly = true)
  public Page<Installation> findAll(Pageable pageable) {
    return installationRepository.findAll(pageable);
  }

  /**
   * Returns one installation.
   *
   * @param id the id of the installation
   * @return the installation with its lines
   * @throws ResourceNotFoundException if the installation does not exist
   */
  @Transactional(readOnly = true)
  public Installation findByIdOrThrow(UUID id) {
    return installationRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Installation", id.toString()));
  }

  /**
   * Writes the record before the first artifact. The unique index on the package id allows one
   * record for each package. A second install of the same package that runs at the same time waits
   * here until the first install commits. Then the database refuses the record, before the install
   * changes the registry.
   */
  private void claim(Installation installation) {
    installationRepository.saveAndFlush(installation);
  }

  private static UniqueConstraintViolationException alreadyInstalled(String packageId) {
    return new UniqueConstraintViolationException(
        "Package '%s' is already installed on this instance.".formatted(packageId));
  }

  /**
   * Checks a member before the install changes something. The platform requires a name and a
   * description for each Data structure. The member gives them, or its document gives them as
   * {@code title} and {@code description}.
   */
  private static PreparedMember prepare(PackageMemberInputDTO member) {
    String name = firstWithText(member.getName(), member.getContent().get(TITLE));
    if (name == null) {
      throw new InvalidInputException(
          NAME,
          member.getUrn(),
          "Member '%s' needs a name or a document title.".formatted(member.getUrn()));
    }
    if (name.length() > InstalledArtifact.NAME_LENGTH) {
      throw new InvalidInputException(
          NAME,
          member.getUrn(),
          "The name of member '%s' must not exceed %d characters."
              .formatted(member.getUrn(), InstalledArtifact.NAME_LENGTH));
    }
    String description =
        firstWithText(member.getDescription(), member.getContent().get(DESCRIPTION));
    if (description == null) {
      throw new InvalidInputException(
          DESCRIPTION,
          member.getUrn(),
          "Member '%s' needs a description or a document description.".formatted(member.getUrn()));
    }
    return new PreparedMember(member, name, description);
  }

  /** Returns the first value that is a string with text, or null if no value has text. */
  private static String firstWithText(String value, Object fallback) {
    if (value != null && !value.isBlank()) {
      return value;
    }
    if (fallback instanceof String text && !text.isBlank()) {
      return text;
    }
    return null;
  }

  /**
   * Creates the Data structure first, because the backend makes the URN of the model from the id of
   * the Data structure. Then stores the model as the first version of the Data structure.
   */
  private InstalledArtifact installDataStructure(PreparedMember prepared) {
    DataStructureInputDTO structureInput = new DataStructureInputDTO();
    structureInput.setName(prepared.name());
    structureInput.setDescription(prepared.description());
    DataStructure structure = dataStructureService.create(structureInput);

    DataStructureVersionInputDTO versionInput = new DataStructureVersionInputDTO();
    versionInput.setDataStructureId(structure.getId());
    versionInput.setDescription(prepared.description());
    versionInput.setModelName(prepared.name());
    versionInput.setModel(withoutRootId(prepared.member().getContent()));
    DataStructureVersion version = dataStructureVersionService.create(versionInput);

    InstalledArtifact artifact = new InstalledArtifact();
    artifact.setArtifactType(InstalledArtifactType.DATA_STRUCTURE);
    artifact.setName(prepared.name());
    artifact.setShellId(structure.getId());
    artifact.setUrn(modelRegistryGateway.logicalUrn(version.getModelUrn()));
    artifact.setVersionedUrn(version.getModelUrn());
    artifact.setOrigin(prepared.member().getUrn());
    return artifact;
  }

  /**
   * Returns a copy of the model without the root {@code $id}. The root {@code $id} is the identity
   * of the member in the package, and the record keeps it as the origin. The platform refuses a
   * root {@code $id} that names another Data structure. The backend replaces the {@code $id} of
   * each {@code $defs} member itself, so the other keys stay as they are.
   */
  private static Map<String, Object> withoutRootId(Map<String, Object> content) {
    Map<String, Object> model = new LinkedHashMap<>(content);
    model.remove(ID);
    return model;
  }
}
