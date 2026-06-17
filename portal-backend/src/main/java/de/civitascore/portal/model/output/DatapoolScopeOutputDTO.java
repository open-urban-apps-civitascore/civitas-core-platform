package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.DatapoolScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/** Output DTO representing the Datapool scope configuration of a DataSource. */
@Data
public class DatapoolScopeOutputDTO {

  @Schema(
      description = "Scope type defining DataPool access",
      example = "ALL",
      allowableValues = {"ALL", "NONE", "SPECIFIC"})
  private DatapoolScopeType type;

  @Schema(
      description = "List of DataPool IDs. Populated only when type is SPECIFIC, empty otherwise.",
      example = "[\"550e8400-e29b-41d4-a716-446655440000\"]")
  private List<UUID> datapoolIds = new ArrayList<>();
}
