package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.output.summary.DistributionSummaryDTO;
import de.civitascore.portal.model.output.summary.PipelineSummaryDTO;
import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetOutputDTO extends BaseOutputDTO {

  private String identifier;
  private String name;
  private String description;

  private DataSetStatus dataSetStatus;
  private String version;

  private List<PipelineSummaryDTO> pipelines;
  private List<DistributionSummaryDTO> distributions;

  private Boolean openDataAccess;

  private UserSummaryDTO createdBy;
}
