package de.civitascore.portal.model.input;

import de.civitascore.portal.model.embedded.RoleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating role resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleInputDTO extends BaseInputDTO {

  @Schema(example = "Data Manager")
  @NotBlank(message = "Name is required") private String name;

  @Schema(example = "Manages datasets and data sources")
  private String description;

  @Schema(example = "DATA")
  @NotNull(message = "Role type is required") private RoleType roleType;

  @Schema(description = "IDs of permissions to assign to this role")
  private List<UUID> permissionIds;

  @Schema(description = "Whether this role can be modified, defaults to false")
  private Boolean readonly = false;
}
