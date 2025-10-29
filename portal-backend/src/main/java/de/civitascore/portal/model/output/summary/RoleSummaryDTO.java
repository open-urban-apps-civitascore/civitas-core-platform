package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.embedded.RoleType;
import lombok.Data;

@Data
public class RoleSummaryDTO {
  private String id;

  private String title;
  private RoleType roleType;
}
