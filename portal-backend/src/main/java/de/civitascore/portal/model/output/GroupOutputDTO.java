package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupOutputDTO extends BaseOutputDTO {

  @Schema(example = "City Data Team")
  private String name;

  @Schema(example = "Responsible for urban mobility datasets")
  private String description;

  @Schema(description = "Primary contact user for this group")
  private UserSummaryDTO contactUser;

  private List<UserSummaryDTO> members;

  private List<AssignmentOutputDTO> assignments;
}
