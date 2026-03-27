package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.DistributionSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO representing a resource for API responses. */
@Schema(description = "Resource details")
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceOutputDTO extends BaseOutputDTO {
  private List<DistributionSummaryDTO> distributions;
}
