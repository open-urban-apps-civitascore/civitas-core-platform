package de.civitascore.portal.model.input;

import de.civitascore.portal.model.embedded.UserTitleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserInputDTO extends BaseInputDTO {

  private UserTitleType title = UserTitleType.OTHER;

  @NotBlank(message = "First name is required") private String firstName;

  @NotBlank(message = "Last name is required") private String lastName;

  @NotBlank(message = "Email is required") @Email(regexp = ".+@.+\\..+", message = "Invalid email format") private String email;

  private String phone;
  private String externalId;
  private Boolean active;
  private List<UUID> groupIds;
}
