package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.output.summary.DataStructureVersionUsageSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO representing a data structure for API responses. */
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

  private List<DataStructureVersionUsageSummaryDTO> dataStructureVersions = new ArrayList<>();

  @Schema(
      description =
          "Whether anything references one of this data structure's versions, released or draft."
              + " While true, it cannot be deleted",
      accessMode = Schema.AccessMode.READ_ONLY)
  private boolean inUse;

  @Schema(
      description =
          "Whether a released entity references one of this data structure's versions. While"
              + " true, it cannot be unreleased",
      accessMode = Schema.AccessMode.READ_ONLY)
  private boolean inUseByReleased;
}
