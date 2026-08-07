package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.embedded.ConnectorType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Data source projection for a caller authorized on the consuming dataset rather than on the source
 * itself; the connector configuration is withheld for that reason.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Data source reference without the connector configuration.")
public class DataSourceSummaryDTO extends BaseSummaryNamedDTO {

  @Schema(example = "Real-time traffic sensor data via MQTT broker")
  private String description;

  @Schema(description = "Connector kind the source uses; drives the payload form of a pipeline")
  private ConnectorType connectorType;
}
