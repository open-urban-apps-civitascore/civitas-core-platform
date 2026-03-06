package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.embedded.RoleType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RoleSummaryDTO extends BaseSummaryNamedDTO {
  private RoleType roleType;
  private String description;
  private boolean readonly;
}
