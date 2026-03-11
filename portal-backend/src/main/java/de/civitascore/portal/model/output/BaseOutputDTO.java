package de.civitascore.portal.model.output;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

/**
 * Abstract base class for all output DTOs. Output DTOs contain the entity ID and audit timestamps
 * (createdAt, modifiedAt).
 */
@Data
public abstract class BaseOutputDTO implements Serializable {

  @Schema(
      description = "Unique identifier (UUID)",
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "550e8400-e29b-41d4-a716-446655440000")
  protected UUID id;

  @Schema(
      description = "Timestamp when the resource was created",
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "2025-06-15T10:30:00")
  protected LocalDateTime createdAt;

  @Schema(
      description = "Timestamp when the resource was last modified",
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "2025-06-15T14:22:00")
  protected LocalDateTime modifiedAt;
}
