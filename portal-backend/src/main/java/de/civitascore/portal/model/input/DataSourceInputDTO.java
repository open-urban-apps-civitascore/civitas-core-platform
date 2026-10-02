package de.civitascore.portal.model.input;

import de.civitascore.portal.model.connector.MqttConnectorConfiguration;
import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating data source resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceInputDTO extends DataSourceMetaInputDTO {

  @Schema(description = "Type of connector (e.g. MQTT, SQL)")
  private ConnectorType connectorType;

  @Schema(
      oneOf = {MqttConnectorConfiguration.class, SqlConnectorConfiguration.class},
      description = "Connector-specific configuration. Structure depends on connectorType.")
  private Map<String, Object> configuration;

  @Schema(description = "ID of the data structure version to associate")
  private UUID dataStructureVersionId;
}
