package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.ActivitySummaryDTO;
import de.civitascore.portal.model.output.summary.DataSetSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "Agent details")
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentOutputDTO extends BaseOutputDTO {
  private String name;
  private List<DataSetSummaryDTO> dataSets;
  private List<ActivitySummaryDTO> activities;
}
