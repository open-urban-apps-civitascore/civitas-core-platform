package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.output.summary.PermissionSummaryDTO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RoleOutputDTO extends BaseOutputDTO {
  private String name;
  private String description;
  private RoleType roleType;
  private List<PermissionSummaryDTO> permissions;
}
