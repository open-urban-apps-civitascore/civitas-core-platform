package de.civitascore.portal.model.input;

import de.civitascore.portal.model.embedded.UserTitleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserInputDTO extends BaseInputDTO {

  @Schema(description = "Salutation, defaults to OTHER", example = "MS")
  private UserTitleType title = UserTitleType.OTHER;

  @Schema(example = "Jane")
  @NotBlank(message = "First name is required") private String firstName;

  @Schema(example = "Doe")
  @NotBlank(message = "Last name is required") private String lastName;

  @Schema(example = "jane.doe@example.com")
  @NotBlank(message = "Email is required") @Email(regexp = ".+@.+\\..+", message = "Invalid email format") private String email;

  @Schema(example = "+49 170 1234567")
  private String phone;

  @Schema(description = "Whether the user account is active", example = "true")
  private Boolean active;

  @Schema(description = "IDs of groups to assign the user to")
  private List<UUID> groupIds;
}
