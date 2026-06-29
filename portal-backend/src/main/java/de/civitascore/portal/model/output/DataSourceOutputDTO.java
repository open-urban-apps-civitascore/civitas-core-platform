package de.civitascore.portal.model.output;

import de.civitascore.portal.model.connector.MqttConnectorConfiguration;
import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.output.summary.DataStructureVersionSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
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

  @Schema(description = "Type of connector (e.g. MQTT, SQL)", example = "MQTT")
  private ConnectorType connectorType;

  @Schema(
      oneOf = {MqttConnectorConfiguration.class, SqlConnectorConfiguration.class},
      description = "The configuration object, structure depends on the connector type")
  private Map<String, Object> configuration;

  private DataStructureVersionSummaryDTO dataStructureVersion;

  @Schema(
      description = "Whether this data source is currently referenced by a pipeline",
      accessMode = Schema.AccessMode.READ_ONLY)
  private boolean inUse;

  @Schema(description = "Datapool scope configuration")
  private DatapoolScopeOutputDTO datapoolScope;
}
