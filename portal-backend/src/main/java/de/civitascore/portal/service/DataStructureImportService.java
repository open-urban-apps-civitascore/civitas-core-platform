package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureImportInputDTO;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a complete data structure in one call: creates the {@link DataStructure} shell, its first
 * {@link DataStructureVersion}, and stores the model in Model Forge — the same path the two-step UI
 * flow takes, composed into a single transaction.
 *
 * <p>All heavy lifting stays in the existing services: {@link DataStructureVersionService}
 * validates the model as a JSON Schema and stores it via the registry gateway, mirroring the
 * assigned version and URN pin onto the shell. Because both creates join this method's transaction,
 * a rejected model rolls the shell back too — the caller never ends up with an empty structure.
 * Everything is created in DRAFT; releasing stays a separate, permission-gated step.
 *
 * <p>The model must declare a {@code $id} that is a {@code :datastructure:} CORE URN. The registry
 * types the stored artifact solely from that {@code $id}: without it the model would silently be
 * registered as a plain Element — the shell would pin the wrong artifact type, and every re-import
 * would mint a fresh identity (duplicates) instead of versioning the same artifact. The UI's UML
 * editor stamps this URN itself; an import caller has to bring it.
 */
@Service
@RequiredArgsConstructor
public class DataStructureImportService {

  private final DataStructureService dataStructureService;
  private final DataStructureVersionService dataStructureVersionService;
  private final ModelRegistryGateway modelRegistryGateway;
  private final DataStructureRepository dataStructureRepository;
  private final DataStructureVersionRepository dataStructureVersionRepository;

  /**
   * A bundle-import resolution: the version a containing artifact should reference, and whether it
   * came from an already installed structure ({@code reused}) or was created by this call.
   */
  public record ImportResolution(DataStructureVersion version, boolean reused) {}

  /**
   * Creates a data structure with its first version and model content.
   *
   * @param input the import input carrying structure metadata, the model and optional styles
   * @return the created first version, with the parent structure and the registry pin set
   * @throws de.civitascore.portal.util.InvalidInputException if the model does not declare a {@code
   *     :datastructure:} URN as {@code $id}, or is not a valid JSON Schema (the whole import is
   *     rolled back)
   * @throws de.civitascore.portal.util.UniqueConstraintViolationException if a shell already pins
   *     this model identity — re-importing would duplicate the shell (409); updating an existing
   *     installation is a separate, not-yet-built flow
   */
  @Transactional
  public DataStructureVersion importDataStructure(DataStructureImportInputDTO input) {
    String modelId = requireDataStructureId(input);
    rejectAlreadyInstalled(modelId);
    return create(input);
  }

  /**
   * Bundle variant of {@link #importDataStructure}: instead of rejecting an already installed model
   * identity, it resolves it — per contained structure the dataset import distinguishes create (URN
   * unknown), reuse (URN installed with identical content; two use cases sharing one Fachmodell is
   * the point of URN identity) and conflict (URN installed with different content).
   *
   * @param input the import input for one bundled structure
   * @return the version to reference, flagged whether it was reused or created
   * @throws de.civitascore.portal.util.UniqueConstraintViolationException if the identity is
   *     installed with different content (409)
   */
  @Transactional
  public ImportResolution importOrReuse(DataStructureImportInputDTO input) {
    String modelId = requireDataStructureId(input);
    String logicalUrn = modelRegistryGateway.logicalUrn(modelId);

    Optional<DataStructureVersion> installed =
        dataStructureVersionRepository.findFirstByModelUrnStartingWith(logicalUrn + ":");
    if (installed.isPresent()) {
      DataStructureVersion version = installed.get();
      if (!modelRegistryGateway.isUnchanged(
          version.getModelUrn(), input.getModel(), input.getStyles())) {
        throw new UniqueConstraintViolationException(
            ("A data structure for model '%s' is already installed with different content."
                    + " Updating an existing installation is not supported yet.")
                .formatted(logicalUrn));
      }
      return new ImportResolution(version, true);
    }

    // No pinned version found. The shell turnstile still applies: a shell without a resolvable
    // version pin (e.g. a half-built UI draft) must conflict rather than gain a twin.
    rejectAlreadyInstalled(modelId);
    return new ImportResolution(create(input), false);
  }

  /**
   * Releases the version and its parent structure when still in DRAFT (version first — the
   * structure's release validation requires a released version). No-op for anything already
   * AVAILABLE.
   *
   * <p>The bundle import calls this for every structure it touches: an imported catalogue artifact
   * is finished content, and {@link DataSourceService} only links sources to AVAILABLE versions —
   * DRAFT would make every bundled source fail. Note that this releases implicitly under the
   * dataset-import permission rather than {@code DATASTRUCTURE_RELEASE}; flagged as an upstream
   * design question (structure releases trigger no sagas, so this is a pure status flip).
   */
  @Transactional
  public void ensureAvailable(DataStructureVersion version) {
    if (version.getDataStructureVersionStatus() == DataStructureVersionStatus.DRAFT) {
      dataStructureVersionService.release(version.getId());
    }
    DataStructure structure = version.getDataStructure();
    if (structure.getDataStructureStatus() == DataStructureStatus.DRAFT) {
      dataStructureService.release(structure.getId());
    }
  }

  private DataStructureVersion create(DataStructureImportInputDTO input) {
    DataStructureInputDTO structureInput = new DataStructureInputDTO();
    structureInput.setName(input.getName());
    structureInput.setDescription(input.getDescription());
    structureInput.setCreatedFromDataSource(false);
    structureInput.setAssignments(input.getAssignments());
    DataStructure structure = dataStructureService.create(structureInput);

    DataStructureVersionInputDTO versionInput = new DataStructureVersionInputDTO();
    versionInput.setDataStructureId(structure.getId());
    versionInput.setDataStructureVersionSource(DataStructureVersionSource.OWN);
    versionInput.setDescription(input.getVersionDescription());
    versionInput.setModelName(input.getModelName());
    versionInput.setModel(input.getModel());
    versionInput.setStyles(input.getStyles());
    return dataStructureVersionService.create(versionInput);
  }

  private String requireDataStructureId(DataStructureImportInputDTO input) {
    Object id = input.getModel().get("$id");
    if (id instanceof String urn && modelRegistryGateway.isDataStructureUrn(urn)) {
      return urn;
    }
    throw new InvalidInputException(
        "DataStructure",
        input.getName(),
        "The model must declare its identity: '$id' has to be a CORE URN of artifact type"
            + " 'datastructure' (urn:core:<scope>:<owner>:datastructure:<domain>:<name>:"
            + "<disambiguator>). Without it the model would be registered as a plain Element and"
            + " re-imports would create duplicates instead of new versions.");
  }

  /**
   * Rejects the import when a shell already pins this model identity. The registry itself is
   * idempotent (same URN → same artifact), but the shell layer is not — without this guard every
   * repeated install would add another {@code data_structures} row pointing at the same artifact.
   */
  private void rejectAlreadyInstalled(String modelId) {
    String logicalUrn = modelRegistryGateway.logicalUrn(modelId);
    if (dataStructureRepository.existsByModelLogicalUrn(logicalUrn)) {
      throw new UniqueConstraintViolationException(
          ("A data structure for model '%s' is already installed. Re-importing would create a"
                  + " duplicate; updating an existing installation is not supported yet.")
              .formatted(logicalUrn));
    }
  }
}
