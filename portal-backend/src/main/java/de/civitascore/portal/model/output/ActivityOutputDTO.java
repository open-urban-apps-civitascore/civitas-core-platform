package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.AgentSummaryDTO;
import de.civitascore.portal.model.output.summary.DistributionSummaryDTO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityOutputDTO extends BaseOutputDTO {
  private String name;
  private String description;
  private List<AgentSummaryDTO> agents;
  private List<DistributionSummaryDTO> distributions;
}
