package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.output.summary.DataEntitySummaryDTO;
import de.civitascore.portal.model.output.summary.GroupSummaryDTO;
import de.civitascore.portal.model.output.summary.RoleSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AssignmentOutputDTO extends BaseOutputDTO {

  private GroupSummaryDTO group;

  private RoleSummaryDTO role;

  @Schema(example = "DATASET")
  private ScopeType scopeType;

  @Schema(
      description = "The specific resource this assignment is scoped to (null for TENANT scope)")
  private DataEntitySummaryDTO scope;
}
