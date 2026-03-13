package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.output.summary.DataStructureVersionSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureOutputDTO extends BaseOutputDTO {

  @Schema(example = "Traffic Sensor Schema")
  private String name;

  @Schema(example = "Schema for traffic sensor readings")
  private String description;

  @Schema(example = "ACTIVE")
  private DataStructureStatus dataStructureStatus;

  @Schema(description = "Whether this was auto-generated from a data source")
  private Boolean createdFromDataSource;

  private List<DataStructureVersionSummaryDTO> dataStructureVersions;

  @Schema(
      description = "Whether this data structure is currently referenced",
      accessMode = Schema.AccessMode.READ_ONLY)
  private boolean inUse;
}
