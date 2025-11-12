package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.AssignmentType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.output.summary.GroupSummaryDTO;
import de.civitascore.portal.model.output.summary.RoleSummaryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AssignmentOutputDTO extends BaseOutputDTO {
  private GroupSummaryDTO group;
  private RoleSummaryDTO role;
  private ScopeType scopeType;
  private String scopeId;
  private AssignmentType assignmentType;
  private Boolean isInherited;
  private AssignmentOutputDTO parentAssignment;
  private String tenantId;
}
