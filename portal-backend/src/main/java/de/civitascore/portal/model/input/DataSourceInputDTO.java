package de.civitascore.portal.model.input;

import de.civitascore.portal.model.connector.MqttConnectorConfiguration;
import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceInputDTO extends DataSourceMetaInputDTO {
  private ConnectorType connectorType;

  @Schema(
      oneOf = {MqttConnectorConfiguration.class, SqlConnectorConfiguration.class},
      description = "Connector-specific configuration. Structure depends on connectorType.")
  private Map<String, Object> configuration;
}
