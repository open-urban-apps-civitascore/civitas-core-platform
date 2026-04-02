package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating agent resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentInputDTO extends BaseInputDTO {
  @NotBlank(message = "Name is required") private String name;
}
