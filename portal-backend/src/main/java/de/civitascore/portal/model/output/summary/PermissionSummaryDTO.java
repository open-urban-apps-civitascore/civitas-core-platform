package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.embedded.PermissionType;
import lombok.Data;

@Data
public class PermissionSummaryDTO {
  private String id;

  private String title;
  private PermissionType permissionType;
}
