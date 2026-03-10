package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetInputDTO extends BaseDataEntityInputDTO {

  @NotBlank(message = "Name is required and must be between 3 and 255 characters") @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters") private String name;

  private String description;

  private Boolean openDataAccess = false;
}
