package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.ScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import java.util.UUID;
import lombok.Data;

@Data
@Schema(description = "Assignment summary for the authenticated user's /me endpoint")
public class MeAssignmentOutputDTO {

  @Schema(description = "Type of scope this assignment applies to", example = "DATASET")
  private ScopeType scopeType;

  @Schema(
      description = "ID of the scoped entity (null for TENANT scope)",
      example = "550e8400-e29b-41d4-a716-446655440000")
  private UUID scopeId;

  @Schema(
      description = "Effective permissions granted by this assignment",
      example = "[\"DATASET_READ\", \"DATASET_CREATE\"]")
  private Set<String> permissions;
}
