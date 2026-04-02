package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.UserTitleType;
import de.civitascore.portal.model.output.summary.GroupSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO representing a user for API responses. */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserOutputDTO extends BaseOutputDTO {

  @Schema(description = "Salutation (e.g. MR, MS, OTHER)", example = "MS")
  private UserTitleType title;

  @Schema(example = "Jane")
  private String firstName;

  @Schema(example = "Doe")
  private String lastName;

  @Schema(example = "jane.doe@example.com")
  private String email;

  @Schema(example = "+49 170 1234567")
  private String phone;

  @Schema(description = "Whether the user account is active", example = "true")
  private Boolean active;

  private List<GroupSummaryDTO> groups = new ArrayList<>();
}
