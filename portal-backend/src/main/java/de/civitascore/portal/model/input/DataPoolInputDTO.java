package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating datapool resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataPoolInputDTO extends BaseDataEntityInputDTO {

  @NotBlank(message = "Name is required and must be between 3 and 255 characters") @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters") private String name;

  @NotBlank(message = "Description is required") private String description;

  @Schema(description = "ID of the user designated as the contact person for this datapool")
  private UUID contactPersonId;
}
