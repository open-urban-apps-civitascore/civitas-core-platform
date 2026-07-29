package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureImportInputDTO;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a complete data structure in one call: creates the {@link DataStructure} shell, its
 * first {@link DataStructureVersion}, and stores the model in Model Forge — the same path the
 * two-step UI flow takes, composed into a single transaction.
 *
 * <p>All heavy lifting stays in the existing services: {@link DataStructureVersionService}
 * validates the model as a JSON Schema and stores it via the registry gateway, mirroring the
 * assigned version and URN pin onto the shell. Because both creates join this method's
 * transaction, a rejected model rolls the shell back too — the caller never ends up with an empty
 * structure. Everything is created in DRAFT; releasing stays a separate, permission-gated step.
 */
@Service
@RequiredArgsConstructor
public class DataStructureImportService {

  private final DataStructureService dataStructureService;
  private final DataStructureVersionService dataStructureVersionService;

  /**
   * Creates a data structure with its first version and model content.
   *
   * @param input the import input carrying structure metadata, the model and optional styles
   * @return the created first version, with the parent structure and the registry pin set
   * @throws de.civitascore.portal.util.InvalidInputException if the model is not a valid JSON
   *     Schema (the whole import is rolled back)
   */
  @Transactional
  public DataStructureVersion importDataStructure(DataStructureImportInputDTO input) {
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
}
