package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupInputDTO extends BaseInputDTO {

  @NotBlank(message = "Title is required") private String title;

  private String description;
  private String contactUserId;
  private String parentGroupId;
  private List<String> memberIds;
  private List<String> systemRoleIds;
}
