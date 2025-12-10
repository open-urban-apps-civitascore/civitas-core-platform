package de.civitascore.portal.model.output.summary;

import java.util.UUID;
import lombok.Data;

@Data
public class DistributionSummaryDTO {
  private UUID id;
  private String accessUrl;
}
