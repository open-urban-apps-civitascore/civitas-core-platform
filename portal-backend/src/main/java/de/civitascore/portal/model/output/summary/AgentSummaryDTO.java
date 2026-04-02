package de.civitascore.portal.model.output.summary;

import java.util.UUID;
import lombok.Data;

/** Lightweight summary DTO for agent entities, used in list endpoints and nested references. */
@Data
public class AgentSummaryDTO {
  private UUID id;
  private String name;
}
