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
import de.civitascore.portal.modelregistry.DataStructureUrns;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * <p>An installation is a copy, not a shared identity. Every artifact a package ships is created
 * under a URN this instance mints — the same scheme the editor uses — and the URN the member
 * carried in the package is recorded on its journal line as its origin. Identities inside the
 * package therefore only serve to let members refer to one another; on install each reference is
 * rewritten from the package URN to the minted one before the referring member is stored.
 */
@Service
@RequiredArgsConstructor
public class InstallationService {

  private static final String ID = "$id";
  private static final String DEFS = "$defs";
  private static final String REF = "$ref";

  private final InstallationRepository installationRepository;
  private final DataStructureService dataStructureService;
  private final DataStructureVersionService dataStructureVersionService;
  private final ModelRegistryGateway modelRegistryGateway;

  @Transactional
  public Installation install(InstallationInputDTO input) {
    PackageManifestInputDTO manifest = input.getPackageManifest();
    // A policy, not a technical limit: two copies of the same package on one instance serve
    // nobody. Updating means installing the new version and uninstalling the old one.
    if (installationRepository.existsByPackageId(manifest.getId())) {
      throw new UniqueConstraintViolationException(
          ("Package '%s' is already installed on this instance; updating an installation is not"
                  + " supported yet.")
              .formatted(manifest.getId()));
    }

    Installation installation = new Installation();
    installation.setPackageId(manifest.getId());
    installation.setPackageVersion(manifest.getVersion());

    // Package URN → minted URN, for every identity this install has created so far. Members are
    // processed in list order, which is enough while data structures are the only kind: they
    // reference nothing inside the package. The kinds that do reference each other arrive with
    // the dependency ordering derived from those references, and read this map to rewrite them.
    Map<String, String> minted = new LinkedHashMap<>();
    for (PackageMemberInputDTO member : manifest.getMembers()) {
      installation.addArtifact(
          switch (member.getKind()) {
            case DATASTRUCTURE -> installDataStructure(member, minted);
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
   * Creates the shell first, because the minted URN derives from the shell's id — exactly as the
   * editor does it — then stores the model under that identity. The package's own {@code $id}s are
   * replaced, not kept: root and every inline {@code $defs} member get the minted URNs, and the
   * replaced values are remembered so later members can be rewritten to point at the copies.
   */
  private InstalledArtifact installDataStructure(
      PackageMemberInputDTO member, Map<String, String> minted) {
    String name = displayName(member);

    DataStructureInputDTO structureInput = new DataStructureInputDTO();
    structureInput.setName(name);
    structureInput.setDescription(member.getDescription());
    structureInput.setCreatedFromDataSource(false);
    DataStructure shell = dataStructureService.create(structureInput);

    String localUrn = DataStructureUrns.dataStructure(name, shell.getId());
    minted.put(member.getUrn(), localUrn);
    Map<String, Object> model = withMintedIdentities(member.getContent(), localUrn, minted);

    DataStructureVersionInputDTO versionInput = new DataStructureVersionInputDTO();
    versionInput.setDataStructureId(shell.getId());
    versionInput.setDataStructureVersionSource(DataStructureVersionSource.OWN);
    versionInput.setDescription(member.getDescription());
    versionInput.setModelName(name);
    versionInput.setModel(model);
    DataStructureVersion version = dataStructureVersionService.create(versionInput);

    InstalledArtifact line = new InstalledArtifact();
    line.setArtifactType(InstalledArtifactType.DATA_STRUCTURE);
    line.setName(name);
    line.setShellId(shell.getId());
    line.setUrn(modelRegistryGateway.logicalUrn(version.getModelUrn()));
    line.setVersionedUrn(version.getModelUrn());
    line.setOrigin(member.getUrn());
    line.setAction(InstalledArtifactAction.CREATED);
    return line;
  }

  /**
   * A copy of the model with the root {@code $id} and each inline {@code $defs} member's {@code
   * $id} replaced by minted URNs. Members that are only a {@code $ref} to something existing are
   * left alone. Only these two levels carry identities the registry would otherwise adopt as its
   * own; deeper {@code $id}s are not part of the CORE data structure contract.
   */
  private static Map<String, Object> withMintedIdentities(
      Map<String, Object> content, String localUrn, Map<String, String> minted) {
    Map<String, Object> model = new LinkedHashMap<>(content);
    rememberOrigin(model.get(ID), localUrn, minted);
    model.put(ID, localUrn);

    if (model.get(DEFS) instanceof Map<?, ?> defs) {
      Map<String, Object> rewrittenDefs = new LinkedHashMap<>();
      defs.forEach(
          (key, def) -> {
            String memberName = String.valueOf(key);
            if (def instanceof Map<?, ?> schema && !schema.containsKey(REF)) {
              Map<String, Object> copy = new LinkedHashMap<>();
              schema.forEach((k, v) -> copy.put(String.valueOf(k), v));
              String elementUrn = DataStructureUrns.elementForMember(localUrn, memberName);
              rememberOrigin(copy.get(ID), elementUrn, minted);
              copy.put(ID, elementUrn);
              rewrittenDefs.put(memberName, copy);
            } else {
              rewrittenDefs.put(memberName, def);
            }
          });
      model.put(DEFS, rewrittenDefs);
    }
    return model;
  }

  private static void rememberOrigin(
      Object packageId, String localUrn, Map<String, String> minted) {
    if (packageId instanceof String origin && !origin.isBlank()) {
      minted.put(origin, localUrn);
    }
  }

  /** The member's name, falling back to the model's title — the URN's name segment needs one. */
  private static String displayName(PackageMemberInputDTO member) {
    if (member.getName() != null && !member.getName().isBlank()) {
      return member.getName();
    }
    if (member.getContent().get("title") instanceof String title && !title.isBlank()) {
      return title;
    }
    throw new InvalidInputException(
        "name", member.getUrn(), "A data structure member needs a name or a model title.");
  }
}
