package de.civitascore.portal.model.input;

import de.civitascore.portal.model.embedded.PermissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionInputDTO extends BaseInputDTO {
  @NotBlank(message = "Name is required") private String name;

  @NotBlank(message = "Title is required") private String title;

  private String description;

  @NotNull(message = "Permission type is required") private PermissionType permissionType;

  private Boolean userModifiable;
  private Boolean isDefault;
}
