package de.civitascore.portal.model.input;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * The metadata of a data structure version: the fields that stay changeable while the version is
 * released. The model, its styles and the model name are not part of it: the model name repeats the
 * diagram name in the styles.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureVersionMetaInputDTO extends BaseInputDTO {

  private String description;
}
