package de.civitascore.portal.model.input;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserInputDTO extends BaseInputDTO {
  @NotBlank(message = "First name is required") private String firstName;

  @NotBlank(message = "Last name is required") private String lastName;

  @NotBlank(message = "Email is required") @Email(message = "Invalid email format") private String email;

  private String phone;
  private String externalId;
  private Boolean active;
  private List<String> groupIds;
}
