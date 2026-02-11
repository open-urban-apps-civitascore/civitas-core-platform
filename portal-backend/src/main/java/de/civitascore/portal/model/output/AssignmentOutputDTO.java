package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.output.summary.GroupSummaryDTO;
import de.civitascore.portal.model.output.summary.RoleSummaryDTO;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AssignmentOutputDTO extends BaseOutputDTO {
  private GroupSummaryDTO group;
  private RoleSummaryDTO role;
  private ScopeType scopeType;
  private UUID scopeId;
}
