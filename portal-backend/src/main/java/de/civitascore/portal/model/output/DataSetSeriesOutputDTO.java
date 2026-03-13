package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.DataSetSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "Dataset series details")
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetSeriesOutputDTO extends BaseOutputDTO {
  private String name;
  private String description;
  private List<DataSetSummaryDTO> dataSets;
}
