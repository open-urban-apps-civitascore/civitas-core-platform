package de.civitascore.portal.model.input;

import de.civitascore.portal.model.embedded.DatapoolScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/** Input DTO for the Datapool scope configuration of a DataSource. */
@Data
public class DatapoolScopeInputDTO {

  @Schema(
      description = "Scope type defining DataPool access",
      example = "SPECIFIC",
      allowableValues = {"ALL", "NONE", "SPECIFIC"})
  private DatapoolScopeType type;

  @Schema(
      description =
          "List of DataPool IDs when type is SPECIFIC. Required and non-empty for SPECIFIC type.",
      example = "[\"550e8400-e29b-41d4-a716-446655440000\"]")
  private List<UUID> datapoolIds;
}
