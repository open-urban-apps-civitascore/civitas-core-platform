package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.output.summary.PermissionSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RoleOutputDTO extends BaseOutputDTO {

  @Schema(example = "Data Manager")
  private String name;

  @Schema(example = "Manages datasets and data sources")
  private String description;

  @Schema(example = "DATA")
  private RoleType roleType;

  private List<PermissionSummaryDTO> permissions;

  @Schema(description = "Whether this role can be modified, defaults to false", example = "false")
  private Boolean readonly;

  @Schema(
      description = "Number of groups using this role",
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "3")
  private Long groupCount;

  @Schema(
      description = "Number of users with this role (via group assignments)",
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "12")
  private Long userCount;
}
