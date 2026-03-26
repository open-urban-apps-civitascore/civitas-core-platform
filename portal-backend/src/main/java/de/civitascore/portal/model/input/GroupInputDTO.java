package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating group resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class GroupInputDTO extends BaseInputDTO {

  @Schema(example = "City Data Team")
  @NotBlank(message = "Name is required") private String name;

  @Schema(example = "Responsible for urban mobility datasets")
  private String description;

  @Schema(description = "ID of the primary contact user")
  private UUID contactUserId;

  @Schema(description = "IDs of users to add as group members")
  private List<UUID> memberIds;
}
