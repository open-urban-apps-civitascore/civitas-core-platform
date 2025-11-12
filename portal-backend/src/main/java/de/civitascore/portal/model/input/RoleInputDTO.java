package de.civitascore.portal.model.input;

import de.civitascore.portal.model.embedded.RoleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RoleInputDTO extends BaseInputDTO {
  @NotBlank(message = "Name is required") private String name;

  private String description;

  @NotNull(message = "Role type is required") private RoleType roleType;

  private List<String> permissionIds;
  private Boolean isDefault;
  private Boolean userModifiable;
}
