package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.embedded.RoleType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RoleSummaryDTO extends BaseSummaryNamedDTO {

  private RoleType roleType;

  private String description;

  @Schema(description = "Whether this role can be modified")
  private boolean readonly;
}
