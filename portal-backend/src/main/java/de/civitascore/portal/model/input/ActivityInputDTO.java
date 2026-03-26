package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating activity resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityInputDTO extends BaseInputDTO {
  @NotBlank(message = "Name is required") private String name;

  private String description;
  private List<UUID> agentIds;
}
