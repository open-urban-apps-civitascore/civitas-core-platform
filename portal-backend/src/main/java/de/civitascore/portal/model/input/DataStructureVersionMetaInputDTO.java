package de.civitascore.portal.model.input;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * The metadata of a data structure version: the fields that stay changeable while the version is
 * released. The model and its styles are not part of it.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureVersionMetaInputDTO extends BaseInputDTO {

  private String description;

  private String modelName;
}
