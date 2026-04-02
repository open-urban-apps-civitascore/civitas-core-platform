package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.embedded.PermissionType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Lightweight summary DTO for permission entities, used in list endpoints and nested references.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionSummaryDTO extends BaseSummaryNamedDTO {

  private PermissionType permissionType;
}
