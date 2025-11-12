package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.GroupSummaryDTO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserOutputDTO extends BaseOutputDTO {
  private String firstName;
  private String lastName;
  private String email;
  private String phone;
  private String externalId;
  private Boolean active;
  private String tenantId;
  private List<GroupSummaryDTO> groups;
}
