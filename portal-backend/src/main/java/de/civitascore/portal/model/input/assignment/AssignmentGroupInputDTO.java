package de.civitascore.portal.model.input.assignment;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.input.BaseInputDTO;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AssignmentGroupInputDTO extends BaseInputDTO {
  @NotNull(message = "Role ID is required") private UUID roleId;

  /** Scope type is not required for SYSTEM Roles but must be provided for all other roles */
  private ScopeType scopeType;

  /**
   * Scope ID is not required if there is no scopeType or if the scope type is TENANT, but must be
   * provided for all other scope types
   */
  private UUID scopeId;
}
