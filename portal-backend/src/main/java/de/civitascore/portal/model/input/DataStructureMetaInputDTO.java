package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * The metadata of a data structure: the fields that stay changeable while the data structure is
 * released.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureMetaInputDTO extends BaseDataEntityInputDTO {

  @NotBlank(message = "Name is required") private String name;

  @NotBlank(message = "Description is required") private String description;
}
