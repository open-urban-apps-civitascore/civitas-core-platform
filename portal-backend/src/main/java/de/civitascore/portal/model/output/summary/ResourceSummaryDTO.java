package de.civitascore.portal.model.output.summary;

import java.util.UUID;
import lombok.Data;

/** Lightweight summary DTO for resource entities, used in list endpoints and nested references. */
@Data
public class ResourceSummaryDTO {
  private UUID id;
}
