package de.civitascore.portal.model.input;

import de.civitascore.portal.model.embedded.ScopeType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AssignmentInputDTO extends BaseInputDTO {
  @NotNull(message = "Group ID is required") private UUID groupId;

  @NotNull(message = "Role ID is required") private UUID roleId;

  @NotNull(message = "Scope type is required") private ScopeType scopeType;

  private String scopeId;
  private Boolean isInherited;
  private UUID parentAssignmentId;
}
