package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.ActivitySummaryDTO;
import de.civitascore.portal.model.output.summary.DataSetSummaryDTO;
import de.civitascore.portal.model.output.summary.ResourceSummaryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DistributionOutputDTO extends BaseOutputDTO {
  private String accessUrl;
  private ResourceSummaryDTO resource;
  private DataSetSummaryDTO dataSet;
  private ActivitySummaryDTO activity;
}
