package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating data space resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSpaceInputDTO extends BaseInputDTO {
  @NotBlank(message = "Name is required") private String name;

  private String description;
  private UUID ownerUserId;
  private UUID parentDataSpaceId;
  private String externalId;
}
