package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating dataset series resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetSeriesInputDTO extends BaseInputDTO {
  @NotBlank(message = "Name is required") private String name;

  private String description;
}
