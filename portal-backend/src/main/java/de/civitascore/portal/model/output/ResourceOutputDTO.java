package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.DistributionSummaryDTO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceOutputDTO extends BaseOutputDTO {
  private List<DistributionSummaryDTO> distributions;
}
