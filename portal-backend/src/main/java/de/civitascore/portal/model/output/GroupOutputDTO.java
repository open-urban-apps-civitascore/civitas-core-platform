package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.GroupSummaryDTO;
import de.civitascore.portal.model.output.summary.RoleSummaryDTO;
import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupOutputDTO extends BaseOutputDTO {
  private String name;
  private String description;
  private String tenantId;
  private UserSummaryDTO contactUser;
  private GroupSummaryDTO parentGroup;
  private List<UserSummaryDTO> members;
  private List<RoleSummaryDTO> systemRoles;
}
