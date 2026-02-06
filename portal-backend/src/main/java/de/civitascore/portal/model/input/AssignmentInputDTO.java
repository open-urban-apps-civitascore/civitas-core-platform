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

  /** Scope type is not required for SYSTEM Roles but must be provided for all other roles */
  private ScopeType scopeType;

  /**
   * Scope ID is not required if there is no scopeType or if the scope type is PLATFORM, but must be
   * provided for all other scope types
   */
  private UUID scopeId;
}
