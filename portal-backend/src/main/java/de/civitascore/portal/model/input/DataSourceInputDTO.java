package de.civitascore.portal.model.input;

import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.ConnectorJson;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Input DTO for creating and updating data source resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceInputDTO extends DataSourceMetaInputDTO {

  @JsonDeserialize(using = ConnectorJson.RequestDeserializer.class)
  private ConnectorConfiguration connector;

  @Schema(description = "ID of the data structure version to associate")
  private UUID dataStructureVersionId;
}
