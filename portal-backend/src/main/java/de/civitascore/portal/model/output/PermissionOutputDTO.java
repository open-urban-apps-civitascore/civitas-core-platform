package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.PermissionCategory;
import de.civitascore.portal.model.embedded.PermissionName;
import de.civitascore.portal.model.embedded.PermissionType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionOutputDTO extends BaseOutputDTO {
  private PermissionName name;
  private String description;
  private PermissionType permissionType;
  private PermissionCategory category;
}
