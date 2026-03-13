package de.civitascore.portal.model.input;

import de.civitascore.portal.model.connector.MqttConnectorConfiguration;
import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceInputDTO extends BaseDataEntityInputDTO {

  @Schema(description = "Data source name (required)")
  @NotBlank(message = "Name is required") private String name;

  @Schema(description = "Data source description")
  private String description;

  @Schema(description = "Type of connector (e.g. MQTT, SQL)")
  private ConnectorType connectorType;

  @Schema(
      oneOf = {MqttConnectorConfiguration.class, SqlConnectorConfiguration.class},
      description = "Connector-specific configuration. Structure depends on connectorType.")
  private Map<String, Object> configuration;

  @Schema(description = "ID of the data structure version to associate")
  private UUID dataStructureVersionId;
}
