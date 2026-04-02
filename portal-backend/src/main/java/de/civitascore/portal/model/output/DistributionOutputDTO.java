package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.ActivitySummaryDTO;
import de.civitascore.portal.model.output.summary.DataSetSummaryDTO;
import de.civitascore.portal.model.output.summary.ResourceSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO representing a distribution for API responses. */
@Schema(description = "Distribution details")
@Data
@EqualsAndHashCode(callSuper = true)
public class DistributionOutputDTO extends BaseOutputDTO {
  private String accessUrl;
  private ResourceSummaryDTO resource;
  private DataSetSummaryDTO dataSet;
  private ActivitySummaryDTO activity;
}
