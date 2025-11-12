package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.embedded.RoleType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RoleSummaryDTO extends BaseSummaryDTO {
  private RoleType roleType;
}
