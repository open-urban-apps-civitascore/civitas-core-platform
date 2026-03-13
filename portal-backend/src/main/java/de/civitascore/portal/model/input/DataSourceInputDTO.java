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
  @NotBlank(message = "Name is required") private String name;

  private String description;

  private ConnectorType connectorType;

  @Schema(
      oneOf = {MqttConnectorConfiguration.class, SqlConnectorConfiguration.class},
      description = "Connector-specific configuration. Structure depends on connectorType.")
  private Map<String, Object> configuration;

  private UUID dataStructureVersionId;
}
