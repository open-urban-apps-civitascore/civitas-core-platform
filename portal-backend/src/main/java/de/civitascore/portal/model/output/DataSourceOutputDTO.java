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

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceOutputDTO extends BaseOutputDTO {
  private String name;
  private String description;
  private DataSourceStatus dataSourceStatus;
  private ConnectorType connectorType;

  @Schema(
      oneOf = {MqttConnectorConfiguration.class, SqlConnectorConfiguration.class},
      description = "The configuration object, structure depends on the connector type")
  private Map<String, Object> configuration;

  private DataStructureVersionSummaryDTO dataStructureVersion;
}
