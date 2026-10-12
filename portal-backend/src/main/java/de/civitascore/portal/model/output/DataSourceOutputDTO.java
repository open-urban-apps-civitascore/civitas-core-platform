package de.civitascore.portal.model.output;

import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.ConnectorSchema;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.output.summary.DataStructureVersionSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO representing a data source for API responses. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceOutputDTO extends BaseOutputDTO {

  @Schema(example = "Traffic Sensor MQTT")
  private String name;

  @Schema(example = "Real-time traffic sensor data via MQTT broker")
  private String description;

  @Schema(example = "ACTIVE")
  private DataSourceStatus dataSourceStatus;

  @Schema(implementation = ConnectorSchema.class)
  private ConnectorConfiguration connector;

  @Schema(
      description = "Versioned CORE URN of this DataSource's configuration artifact in Model Forge")
  private String configurationUrn;

  private DataStructureVersionSummaryDTO dataStructureVersion;

  @Schema(
      description =
          "Whether any pipeline references this data source. While true, it cannot be deleted",
      accessMode = Schema.AccessMode.READ_ONLY)
  private boolean inUse;

  @Schema(
      description =
          "Whether a Pipeline of a released Dataset references this data source. While true, the"
              + " data source cannot be unreleased and its technical fields are locked",
      accessMode = Schema.AccessMode.READ_ONLY)
  private boolean inUseByReleased;

  @Schema(description = "Datapool scope configuration")
  private DatapoolScopeOutputDTO datapoolScope;
}
