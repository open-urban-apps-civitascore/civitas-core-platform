package de.civitascore.portal.model.output.summary;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.UUID;
import lombok.Data;

@Data
public abstract class BaseSummaryDTO implements Serializable {

  @Schema(
      description = "Unique identifier (UUID)",
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "550e8400-e29b-41d4-a716-446655440000")
  private UUID id;
}
