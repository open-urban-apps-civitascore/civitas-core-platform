package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.DataPoolSummaryDTO;
import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO representing a group for API responses. */
@Data
@EqualsAndHashCode(callSuper = true)
public class GroupOutputDTO extends BaseOutputDTO {

  @Schema(description = "Keycloak group ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
  private String externalId;

  @Schema(example = "City Data Team")
  private String name;

  @Schema(example = "Responsible for urban mobility datasets")
  private String description;

  @Schema(description = "Primary contact user for this group")
  private UserSummaryDTO contactUser;

  private List<UserSummaryDTO> members = new ArrayList<>();

  private List<AssignmentOutputDTO> assignments = new ArrayList<>();

  private List<DataPoolSummaryDTO> datapools = new ArrayList<>();
}
