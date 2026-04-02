package de.civitascore.portal.model.input.assignment;

import de.civitascore.portal.model.input.BaseInputDTO;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for scoped assignments embedded in data entity creation requests. */
@Data
@EqualsAndHashCode(callSuper = true)
public class AssignmentScopedInputDTO extends BaseInputDTO {
  @NotNull(message = "Group ID is required") private UUID groupId;

  @NotNull(message = "Role ID is required") private UUID roleId;
}
