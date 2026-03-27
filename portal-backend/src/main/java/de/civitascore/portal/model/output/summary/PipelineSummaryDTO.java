package de.civitascore.portal.model.output.summary;

import java.util.UUID;
import lombok.Data;

/** Lightweight summary DTO for pipeline entities, used in list endpoints and nested references. */
@Data
public class PipelineSummaryDTO {
  private UUID id;
  private String name;
  private String description;
}
