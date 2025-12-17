package de.civitascore.portal.model.output.summary;

import java.util.UUID;
import lombok.Data;

@Data
public class AgentSummaryDTO {
  private UUID id;
  private String name;
}
