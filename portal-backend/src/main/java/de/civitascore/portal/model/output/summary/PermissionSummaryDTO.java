package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.embedded.PermissionType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionSummaryDTO extends BaseSummaryDTO {
  private PermissionType permissionType;
}
