package de.civitascore.portal.model.input;

import de.civitascore.portal.model.embedded.ScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AssignmentInputDTO extends BaseInputDTO {
  @NotBlank(message = "Group ID is required") private String groupId;

  @NotBlank(message = "Role ID is required") private String roleId;

  @NotNull(message = "Scope type is required") private ScopeType scopeType;

  private String scopeId;
  private Boolean isInherited;
  private String parentAssignmentId;
  private String metadata;
}
