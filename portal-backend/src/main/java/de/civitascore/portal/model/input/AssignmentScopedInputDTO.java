package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AssignmentScopedInputDTO extends BaseInputDTO {
  @NotNull(message = "Group ID is required") private UUID groupId;

  @NotNull(message = "Role ID is required") private UUID roleId;
}
